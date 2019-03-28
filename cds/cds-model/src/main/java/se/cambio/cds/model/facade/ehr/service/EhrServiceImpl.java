package se.cambio.cds.model.facade.ehr.service;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;

import se.cambio.cds.model.facade.ehr.delegate.EhrService;
import se.cambio.cds.model.facade.ehr.util.EHRDataStream;
import se.cambio.cds.model.instance.ArchetypeReference;
import se.cambio.cm.model.ehr.dto.EhrDTO;
import se.cambio.cm.model.facade.administration.delegate.ClinicalModelsService;

public class EhrServiceImpl implements EhrService{
	
	@Autowired
	private ClinicalModelsService clinicalModelsService;

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
		Collection<EhrDTO> ehrs = clinicalModelsService.getAllCMElements(EhrDTO.class);
		for(ArchetypeReference ar : archetypeReferences){
			System.out.println("--------------------------------");
			System.out.println(ar.toString());
			System.out.println("--------------------------------");
		}
		// TODO Auto-generated method stub
		return new HashMap<String, Collection<ArchetypeReference>>();
	}

	@Override
	public Set<String> fetchEhrIds(DateTime beforeTimestamp, DateTime afterTimestamp, Collection<String> archetypeIds) {
		// TODO Auto-generated method stub
		return null;
	}

}
