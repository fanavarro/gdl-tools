package se.cambio.cm.model.ehr.dto;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import se.cambio.cm.model.util.CMElement;

public class EhrDTO implements CMElement{
	/**
	 * 
	 */
	private static final long serialVersionUID = -2943110411752082977L;
	
	private String id;
	private String format;
	private String source;
	private Date lastUpdate;
	private Document xml;
	
	@Override
	public String getId() {
		return id;
	}
	
	@Override
	public void setId(String id) {
		this.id = id;
	}
	
	@Override
	public String getFormat() {
		return format;
	}
	
	@Override
	public void setFormat(String format) {
		this.format = format;
	}
	
	@Override
	public String getSource() {
		return source;
	}
	
	@Override
	public void setSource(String source) {
		this.source = source;
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
			String trimSource = trim(source);
			ByteArrayInputStream input = new ByteArrayInputStream(
					trimSource.getBytes("UTF-8"));
			xml = builder.parse(input);
			xml.normalize();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}       
        
	}
	
	@Override
	public Date getLastUpdate() {
		return lastUpdate;
	}
	
	@Override
	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public Document getXml() {
		return xml;
	}

	public void setXml(Document xml) {
		this.xml = xml;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("EhrDTO [id=");
		builder.append(id);
		builder.append(", format=");
		builder.append(format);
		builder.append(", source=");
		builder.append(source);
		builder.append(", lastUpdate=");
		builder.append(lastUpdate);
		builder.append("]");
		return builder.toString();
	}
	
	private String trim(String input) {
	    BufferedReader reader = new BufferedReader(new StringReader(input));
	    StringBuffer result = new StringBuffer();
	    try {
	        String line;
	        while ( (line = reader.readLine() ) != null)
	            result.append(line.trim());
	        return result.toString();
	    } catch (IOException e) {
	        throw new RuntimeException(e);
	    }
	}

}
