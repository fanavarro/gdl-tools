package se.cambio.cds.util;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.joda.time.DateTime;
import org.openehr.rm.datatypes.basic.DataValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import se.cambio.cds.model.facade.ehr.delegate.EhrService;
import se.cambio.cds.model.facade.ehr.service.EhrParser;
import se.cambio.cds.model.facade.ehr.util.EHRDataStream;
import se.cambio.cds.model.instance.ArchetypeReference;
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
			elements.put(ehr.getId(), new HashSet<ArchetypeReference>());

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

						ElementInstance elementInstanceCopy = new ElementInstance(elementInstance.getId(),
								elementInstance.getDataValue(), archetypeCopy, elementInstance.getContainerInstance(),
								elementInstance.getNullFlavour());
						/*
						 * Buscamos en la historia el dato que necesita el
						 * arquetipo
						 */
						Node node = parser.queryXML(ehr.getXml(), gdlPath);

						/*
						 * Crear dataValue a partir de la informacion del nodo.
						 */
						createDataValueFromNode(node, elementInstanceCopy);
						//elementInstanceCopy.setDataValue(dataValue); (se hace dentro de createDataValueFromNode)
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

	private DataValue createDataValueFromNode(Node xmlNode, ElementInstance elementInstance) {
		// TODO Auto-generated method stub implementaaaaa, vicenteeee
		String attributeName = "";
		String valueStr = null;
		String rmName = "";
		DataValue dv = null;
		// El nodo siempre será un ELEMENT. Recorremos sus nodos hijos.
		// El primer hijo es quien contiene el tipo de dato.
		NodeList childs = xmlNode.getChildNodes();
		for (int i = 0; i < childs.getLength(); i++) {
			Node child = childs.item(i);
			NamedNodeMap namedNodeMap = child.getAttributes();
			rmName = namedNodeMap.getNamedItem("xsi:type").getTextContent();
			NodeList values = child.getChildNodes();
			for (int j = 0; j < values.getLength(); j++) {
				Node valueNode = values.item(j);
				attributeName = valueNode.getNodeName();
				valueStr = valueNode.getTextContent();
				Object object = convertStringToObject(rmName, attributeName, valueStr);
				dv = DVUtil.createDV(elementInstance, rmName, attributeName, object);
				elementInstance.setDataValue(dv);
			}
		}
		return dv;
	}

	@Override
	public Set<String> fetchEhrIds(DateTime beforeTimestamp, DateTime afterTimestamp, Collection<String> archetypeIds) {
		// TODO Auto-generated method stub
		return null;
	}

	private Object convertStringToObject(String rmName, String attributeName, String valueStr) {
		//TODO: completar con el resto de tipos de datos.
		Object value = null;
		switch (rmName) {
		case "DV_BOOLEAN":
			if("value".equalsIgnoreCase(attributeName)){
				value = Boolean.valueOf(valueStr);
			}
			break;
		case "DV_QUANTITY":
			if("units".equalsIgnoreCase(attributeName)){
				value = valueStr;
			} else if ("magnitude".equalsIgnoreCase(attributeName)){
				value = Double.valueOf(valueStr);
			} else if("precision".equalsIgnoreCase(attributeName)){
				value = Integer.valueOf(valueStr);
			}
			break;
		}
		return value;
	}
}
