package se.cambio.openehr.controller.session.data;

import se.cambio.cm.model.archetype.vo.ArchetypeTermVO;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ArchetypeTerms {

    private Map<String, Map<String, Map<String, ArchetypeTermVO>>> archetypeTermsMap = null;

    public ArchetypeTerms() {
    }

    public void loadArchetypeTerms(
            String archetypeId,
            Collection<ArchetypeTermVO> archetypeTerms) {
        getArchetypeTermsMap().remove(archetypeId);
        for (ArchetypeTermVO archetypeTermVO: archetypeTerms) {
            getArchetypeTermsMap(archetypeId, archetypeTermVO.getLanguage()).put(archetypeTermVO.getCode(), archetypeTermVO);
        }
    }

    public ArchetypeTermVO getArchetypeTerm(String archetypeId, String lang, String atCode) {
        return getArchetypeTermsMap(archetypeId, lang).get(atCode);
    }

    private Map<String, ArchetypeTermVO> getArchetypeTermsMap(String archetypeId, String lang) {
        return getArchetypeTermsMap(archetypeId).computeIfAbsent(lang, k -> new HashMap<>());
    }

    private Map<String, Map<String, ArchetypeTermVO>> getArchetypeTermsMap(String archetypeId) {
        return getArchetypeTermsMap().computeIfAbsent(archetypeId, k -> new HashMap<>());
    }

    private Map<String, Map<String, Map<String, ArchetypeTermVO>>> getArchetypeTermsMap() {
        if (archetypeTermsMap == null) {
            archetypeTermsMap = new HashMap<>();
        }
        return archetypeTermsMap;
    }
}
