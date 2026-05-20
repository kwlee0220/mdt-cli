package mdt.cli;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class IdPair {
	private final String m_id;
	private final @Nullable String m_idShort;
	
	public static IdPair of(String id, @Nullable String idShort) {
		return new IdPair(id, idShort);
	}
	
	private IdPair(String id, @Nullable String idShort) {
		m_id = id;
		m_idShort = idShort;
	}
	
	public String getId() {
		return m_id;
	}
	
	public @Nullable String getIdShort() {
		return m_idShort;
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || obj.getClass() != getClass() ) {
			return false;
		}
		
		IdPair other = (IdPair)obj;
		return m_id.equals(other.m_id);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_id);
	}
	
	@Override
	public String toString() {
		if ( m_idShort != null ) {
			return String.format("%s (%s)", m_id, m_idShort);
		}
		else {
			return String.format("%s (none)", m_id);
		}
	}
}
