package se.cambio.cds.model.facade.ehr.service;

import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import se.cambio.cds.model.facade.ehr.delegate.EhrService;
import se.cambio.cds.model.facade.ehr.util.EHRDataStream;
import se.cambio.cds.model.instance.ArchetypeReference;

public class EhrServiceImpl implements EhrService{

	@Override
	public List<List<Object>> query(String sql) {
		return null;
	}

	@Override
	public EHRDataStream queryStream(String sql) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Collection<ArchetypeReference>> queryEHRElements(Collection<String> ehrIds,
			Collection<ArchetypeReference> archetypeReferences, Calendar date) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<String> fetchEhrIds(DateTime beforeTimestamp, DateTime afterTimestamp, Collection<String> archetypeIds) {
		// TODO Auto-generated method stub
		return null;
	}

}
