package se.cambio.cm.model.ehr.dto;

import java.util.Date;

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
	}
	
	@Override
	public Date getLastUpdate() {
		return lastUpdate;
	}
	
	@Override
	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
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
