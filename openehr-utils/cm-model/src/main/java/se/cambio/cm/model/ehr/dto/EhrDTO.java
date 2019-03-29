package se.cambio.cm.model.ehr.dto;

import java.io.IOException;
import java.io.StringReader;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
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
			xml = builder.parse(new InputSource(new StringReader(source)));
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

}
