package se.cambio.openehr.controller.session.data;

import se.cambio.cm.model.ehr.dto.EhrDTO;
import se.cambio.cm.model.facade.administration.delegate.ClinicalModelsService;

public class ElectronicHealthRecords extends AbstractCMManager<EhrDTO> {

	public ElectronicHealthRecords(ClinicalModelsService clinicalModelsService) {
		super(clinicalModelsService);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Class<EhrDTO> getCMElementClass() {
		return EhrDTO.class;
	}

}
