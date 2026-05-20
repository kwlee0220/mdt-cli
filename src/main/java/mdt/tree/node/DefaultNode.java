package mdt.tree.node;

import org.barfuin.texttree.api.Node;

import utils.func.Optionals;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class DefaultNode implements Node {
	private Object m_prefix = "";
	private Object m_title = "";
	private Object m_valueType = "";
	private Object m_value = "";
	private boolean m_hideValue = false;
	private String m_text = null;	// 일반적으로 null이고, 이때는 'getText()'를 호출하게되지만,
		                            // null이 아닌 경우는 'getText()'를 호출하지 않고 이 값을 사용함
	
	public DefaultNode() { }
	public DefaultNode(Object title, Object valueType, Object value) {
		m_title = title;
		m_valueType = valueType;
		m_value = value;
	}

	public Object getPrefix() {
		return m_prefix;
	}

	public void setPrefix(Object prefix) {
		m_prefix = prefix;
	}
	
	public Object getTitle() {
		return m_title;
	}
	
	public void setTitle(Object title) {
		m_title = title;
	}
	
	public void setValueType(Object valueType) {
		m_valueType = valueType;
	}
	
	public Object getValue() {
		return m_value;
	}
	
	public void setValue(Object value) {
		m_value = value;
	}
	
	public void setHideValue(boolean hide) {
		m_hideValue = hide;
	}

	@Override
	public String getText() {
		if ( m_text != null ) {
			return String.format("%s%s", m_prefix, m_text);
		}
		else {
			if ( m_hideValue ) {
				return String.format("%s%s%s", m_prefix, m_title, Optionals.getOrElse(m_valueType, ""));
			}
			else {
				return String.format("%s%s%s: %s", m_prefix, m_title, Optionals.getOrElse(m_valueType, ""),
									Optionals.getOrElse(m_value, "None"));
			}
		}
	}
	
	public void setText(String text) {
		m_text = text;
	}
}
