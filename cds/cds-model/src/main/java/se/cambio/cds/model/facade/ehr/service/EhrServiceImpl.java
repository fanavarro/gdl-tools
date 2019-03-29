package se.cambio.cds.model.facade.ehr.service;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.joda.time.DateTime;
import org.openehr.rm.datatypes.basic.DataValue;
import org.openehr.rm.datatypes.text.DvCodedText;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import se.cambio.cds.model.facade.ehr.delegate.EhrService;
import se.cambio.cds.model.facade.ehr.util.EHRDataStream;
import se.cambio.cds.model.instance.ArchetypeReference;
import se.cambio.cds.model.instance.ContainerInstance;
import se.cambio.cds.model.instance.ElementInstance;
import se.cambio.cm.model.ehr.dto.EhrDTO;
import se.cambio.cm.model.facade.administration.delegate.ClinicalModelsService;

public class EhrServiceImpl implements EhrService {

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
		EhrParser parser = new EhrParser();
		Map<String, Collection<ArchetypeReference>> elements = new HashMap<String, Collection<ArchetypeReference>>();
		Collection<EhrDTO> ehrs = clinicalModelsService.searchCMElementsByIds(EhrDTO.class, ehrIds);
		/* Recorrer las historias clinicas */
		for (EhrDTO ehr : ehrs) {

			/*
			 * El mapa contiene el id de la historia como clave, y un conjunto
			 * de referencias a arquetipos, con los valores establecidos. Aqui
			 * se inicializan los arquetipos como un conjunto vacio.
			 */
			elements.put(ehr.getId(), new TreeSet<ArchetypeReference>());

			/* Recorremos las referencias a arquetipos */
			for (ArchetypeReference ar : archetypeReferences) {
				ArchetypeReference archetypeCopy = new ArchetypeReference(ar.getIdDomain(), ar.getIdArchetype(),
						ar.getIdTemplate());
				Map<String, ElementInstance> map = ar.getElementInstancesMap();
				/*
				 * Recorremos los elementos de instancia de cada referencia de
				 * arquetipo
				 */
				for (Entry<String, ElementInstance> entry : map.entrySet()) {
					/* En key estará el path gdl */
					String gdlPath = entry.getKey();
					/* En value esta el elemento que hay que rellenar */
					ElementInstance elementInstance = entry.getValue();
					System.out.println(gdlPath);
					System.out.println(elementInstance);
					try {
						/*
						 * Buscamos en la historia el dato que necesita el
						 * arquetipo
						 */
						Node node = parser.queryXML(ehr.getXml(), gdlPath);
						/* Crear dataValue a partir de la informacion del nodo. */
						DataValue dataValue = createDataValueFromNode(node);
						ElementInstance elementInstanceCopy = new ElementInstance(elementInstance.getId(), elementInstance.getDataValue(),
								archetypeCopy, elementInstance.getContainerInstance(), elementInstance.getNullFlavour());
						elementInstanceCopy.setDataValue(dataValue);
						archetypeCopy.getElementInstancesMap().put(gdlPath, elementInstanceCopy);

					} catch (XPathExpressionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
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
				elements.get(ehr.getId()).add(archetypeCopy);
			}
		}
		return elements;
	}

	private DataValue createDataValueFromNode(Node node) {
		// TODO Auto-generated method stub implementaaaaa, vicenteeee
		return null;
	}

	@Override
	public Set<String> fetchEhrIds(DateTime beforeTimestamp, DateTime afterTimestamp, Collection<String> archetypeIds) {
		// TODO Auto-generated method stub
		return null;
	}

}
