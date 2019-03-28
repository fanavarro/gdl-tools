package se.cambio.cm.model.util;

import org.springframework.util.Assert;
import se.cambio.cm.model.archetype.dto.ArchetypeDTO;
import se.cambio.cm.model.ehr.dto.EhrDTO;
import se.cambio.cm.model.guide.dto.GuideDTO;
import se.cambio.cm.model.template.dto.TemplateDTO;
import se.cambio.cm.model.terminology.dto.TerminologyDTO;
import se.cambio.openehr.util.exceptions.InstanceNotFoundException;
import se.cambio.openehr.util.exceptions.InternalErrorException;
import se.cambio.openehr.util.exceptions.MissingConfigurationParameterException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class CMTypeManager {

    private static CMTypeManager instance;
    private Map<String, CMType> cmTypeByIdMap;

    private CMTypeManager() {
        registerCMType(new CMType("terminologies", TerminologyDTO.class, Collections.singleton(CMTypeFormat.CSV_FORMAT.getFormat())));
        registerCMType(new CMType("archetypes", ArchetypeDTO.class, Arrays.asList(CMTypeFormat.ADL_FORMAT.getFormat(), CMTypeFormat.ADLS_FORMAT.getFormat())));
        registerCMType(new CMType("templates", TemplateDTO.class, Collections.singleton(CMTypeFormat.OET_FORMAT.getFormat())));
        registerCMType(new CMType("guidelines", GuideDTO.class, Collections.singleton(CMTypeFormat.GDL_FORMAT.getFormat())));
        registerCMType(new CMType("ehrs", EhrDTO.class, Collections.singleton(CMTypeFormat.EHR_FORMAT.getFormat())));
        registerAdditionalCMTypes();
        
    }

    private void registerAdditionalCMTypes() {
        try {
            for (CMType cmType : CMConfigurationManager.getAdditionalCMElements()) {
                registerCMType(cmType);
            }
        } catch (MissingConfigurationParameterException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void registerCMType(CMType cmType) {
        getCmTypeByIdMap().put(cmType.getId(), cmType);
    }

    public CMType getCMTypeById(String id) throws InstanceNotFoundException {
        CMType cmType = getCmTypeByIdMap().get(id);
        if (cmType != null) {
            return cmType;
        } else {
            throw new InstanceNotFoundException(id, CMTypeManager.class.getName());
        }
    }

    public CMType getCMTypeByClass(Class<? extends CMElement> cmElementClass) throws InternalErrorException {
        Assert.notNull(cmElementClass, "CM element class cannot be null");
        for (CMType cmType : getAllCMTypes()) {
            if (cmElementClass.isAssignableFrom(cmType.getCmElementClass())) {
                return cmType;
            }
        }
        throw new InternalErrorException(new InstanceNotFoundException(cmElementClass.getName(), CMElement.class.getName()));
    }

    public Class<? extends CMElement> getCMElementClassById(String id) throws InstanceNotFoundException {
        CMType cmType = getCMTypeById(id);
        return cmType.getCmElementClass();
    }

    public Collection<CMType> getAllCMTypes() {
        return getCmTypeByIdMap().values();
    }


    private Map<String, CMType> getCmTypeByIdMap() {
        if (cmTypeByIdMap == null) {
            cmTypeByIdMap = new LinkedHashMap<>();
        }
        return cmTypeByIdMap;
    }

    public static CMTypeManager getInstance() {
        if (instance == null) {
            instance = new CMTypeManager();
        }
        return instance;
    }

    public <E extends CMElement> CmElementListParameterizedType<E> getCmElementListParameterizedType(Class<E> cmElementClass) {
        return new CmElementListParameterizedType<>(cmElementClass);
    }

    private static class CmElementListParameterizedType<E extends CMElement> implements ParameterizedType {
        private Class<E> cmElementClass;

        CmElementListParameterizedType(Class<E> cmElementClass) {
            this.cmElementClass = cmElementClass;
        }

        public Type getRawType() {
            return List.class;
        }

        public Type getOwnerType() {
            return null;
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{cmElementClass};
        }
    }
}
