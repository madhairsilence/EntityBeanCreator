package harish.syndrome;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EntityBeanCreator {

	private Connection connection;
	private HashMap<String, Table> tables;
	private String vendor;
	private String location;	
	private List<Attribute> attributes;
	private StringBuffer entityBean;

	private final String SERIALIZABLE = "import java.io.Serializable;\n";			
	private final String COLLECTION ="import java.util.Collection;\n";
	private final String PERSISTANCE ="import javax.persistence.*;\n";
	private final String ENTITY = "@Entity\n@Table(name=\"{0}\")\n";
	private final String NEWLINE = "\n";
	private final String CLASS_DECLARATION = "public {0} implements java.io.Serializable{\n\n" +
			"<declaration> \n\n" +
			"}";
	private final String ATTRIBUTE_DECLARATION = "\tprivate {datatype} {attribute};\n";
	private final String GETTER_DECLARATION = "\tpublic {datatype} get{attribute} {\n \t\treturn this.{attribute};\n \t}\n\n";
	private final String SETTER_DECLARATION = "\tpublic void set{attribute}({datatype} {attribute}){\n \t\tthis.{attribute} = {attribute}; \n\t}\n\n";


	private final String CLASS = "class";
	private final String ATTRIBUTE = "attribute";
	public static final String POSTGRES = "select tablename from pg_tables where tablename !~ '^pg_+' and tablename !~ '^sql_+'";

	public EntityBeanCreator(Connection connection, String vendor, String location) {
		this.connection = connection;	
		this.vendor = vendor;
		this.location = location;
		getTables();
	}

	public List<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<Attribute> attributes) {
		this.attributes = attributes;
	}

	public void setTables(HashMap<String,Table> tables) {
		this.tables = tables;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	//Generate Entity Bean for All Tables
	public void generateAllEntityBeans( ){
		try {
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery( this.vendor );
			
			while(resultSet.next()){
				generateEntityBean( resultSet.getString(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
	}

	//Generate EntityBean for Specific Table
	public void generateEntityBean( String tableName ){

		entityBean = new StringBuffer();
		Table table = tables.get(tableName);
		
		entityBean.append( SERIALIZABLE );
		
		entityBean.append( COLLECTION );
		
		entityBean.append( PERSISTANCE );
		
		entityBean.append( NEWLINE );
		
		entityBean.append( ENTITY.replace("{0}", table.getTableName()));	
		
		entityBean.append( CLASS_DECLARATION.replace("{0}", format(tableName,CLASS)));
				
		writeAttributes( table );	//Writing all the attributes with Getters and Setters
		
		writeIntoFile( table.getTableName()+".java", entityBean.toString()); //Writing in to File
	}
	
	//Get List of Tables from the Database
	public HashMap<String,Table> getTables(){
		
		tables = new java.util.HashMap<String, Table>();
		try {

			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery( vendor );
			String tableName = "";
					
			while( resultSet.next()){
				tableName  = resultSet.getString( 1 );
				tables.put( tableName, getTable( tableName ));
				getAttributes( tableName );
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return tables;
	}

	
	private void writeAttributes( Table table ){
		
		List<Attribute> attributes = table.getAttributes();
		Attribute attribute = null;
		Pattern pattern = Pattern.compile("<declaration>");
		Matcher matcher = null;
		StringBuffer attributeBuffer = new StringBuffer();
		
		for( int index=0; index < attributes.size(); index++){
			attribute = attributes.get( index );
			attributeBuffer.append("\t@Column(name=\""+attribute.getColumnName()+"\")\n");
			attributeBuffer.append(ATTRIBUTE_DECLARATION.replace( "{attribute}",attribute.getAttributeName()).replace("{datatype}" , attribute.getDataType() ));
			attributeBuffer.append( NEWLINE );	
			writeGettersAndSetters(attributeBuffer,attribute);
		}
		
		matcher = pattern.matcher( entityBean.toString() );
		matcher.find();
		entityBean.replace( matcher.start(), matcher.end(), attributeBuffer.toString());
	}
	
	private void writeGettersAndSetters(StringBuffer attributeBuffer,Attribute attribute){

		attributeBuffer.append(GETTER_DECLARATION.replace( "{datatype}", attribute.getDataType()).replace("{attribute}", attribute.getAttributeName()));
		attributeBuffer.append(SETTER_DECLARATION.replace( "{datatype}", attribute.getDataType()).replace("{attribute}", attribute.getAttributeName()));
	
	}
	
	private void writeIntoFile(String fileName, String text){
		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(new File(fileName));
			fileWriter.write( text );
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private Table getTable(String tableName) throws SQLException{

		Table table = null;

		table = new Table();
		table.setTableName( format(tableName,CLASS ));				
		table.setAttributes(getAttributes(tableName));
		return table;
	}


	private List<Attribute> getAttributes(String tableName){
		try {
			Statement statement = connection.createStatement();			
			ResultSet resultSet = statement.executeQuery("select * from \""+tableName+"\"");
			ResultSetMetaData metadata = resultSet.getMetaData();

			Attribute attribute = null;
			attributes = new ArrayList<Attribute>();

			for(int index=1;index <= metadata.getColumnCount();index++){

				attribute = new Attribute();
				attribute.setDataType( getStandardType(metadata.getColumnTypeName(index)));
				attribute.setAttributeName(format(metadata.getColumnLabel(index),ATTRIBUTE));
				attribute.setColumnName( metadata.getColumnLabel(index));
				attributes.add( attribute );		

			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return attributes;
	}


	private String getStandardType(String columnType) {
		if(columnType.equalsIgnoreCase("varchar")){
			return "java.lang.String";
		}

		else if(columnType.equalsIgnoreCase("int4")){
			return "java.lang.Integer";
		}

		else{
			return columnType;
		}
	}


	
	private String format(String name, String type) {

		StringTokenizer tokenizer = new StringTokenizer(name,"_");
		String returnString ="";
		String temp = "";
		
		while(tokenizer.hasMoreTokens()){
			temp = tokenizer.nextToken();
			returnString +=  temp.substring(0,1).toUpperCase()+temp.substring(1,temp.length());			
		}

		if( ATTRIBUTE.equals( type )){
			returnString =  returnString.substring(0,1).toLowerCase()+returnString.substring(1,returnString.length());
		}


		return returnString;
	} 
	

}


class Table{

	private String tableName;		
	private List<Attribute> attributes;


	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public List<Attribute> getAttributes() {
		return attributes;
	}
	public void setAttributes(List<Attribute> attributes) {
		this.attributes = attributes;
	}

}


class Attribute{

	private String attributeName;
	private String dataType;
	private String getterMethod;
	private String setterMethod;
	private String columnName;

	public String getColumnName() {
		return columnName;
	}
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}
	public String getAttributeName() {
		return attributeName;
	}
	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}
	public String getDataType() {
		return dataType;
	}
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	public String getGetterMethod() {
		return getterMethod;
	}
	public void setGetterMethod(String getterMethod) {
		this.getterMethod = getterMethod;
	}
	public String getSetterMethod() {
		return setterMethod;
	}
	public void setSetterMethod(String setterMethod) {
		this.setterMethod = setterMethod;
	}

}


