package se.cambio.cds.util;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.log4j.Logger;
import org.openehr.rm.datatypes.basic.DataValue;
import org.openehr.rm.datatypes.quantity.datetime.DvDateTime;
import org.openehr.rm.datatypes.text.CodePhrase;
import org.openehr.rm.datatypes.text.DvCodedText;
import org.openehr.rm.datatypes.text.Match;
import org.openehr.rm.datatypes.text.TermMapping;
import se.cambio.cds.gdl.model.Binding;
import se.cambio.cds.gdl.model.Guide;
import se.cambio.cds.gdl.model.TermBinding;
import se.cambio.cds.gdl.model.expression.ExpressionItem;
import se.cambio.cds.gdl.model.expression.OperatorKind;
import se.cambio.cds.model.facade.execution.vo.GeneratedArchetypeReference;
import se.cambio.cds.model.facade.execution.vo.GeneratedElementInstance;
import se.cambio.cds.model.facade.execution.vo.PredicateGeneratedElementInstance;
import se.cambio.cds.model.facade.execution.vo.RuleReference;
import se.cambio.cds.model.instance.ArchetypeReference;
import se.cambio.cds.model.instance.ElementInstance;
import se.cambio.openehr.controller.session.OpenEHRSessionManager;
import se.cambio.openehr.util.ExceptionHandler;
import se.cambio.openehr.util.OpenEHRConst;
import se.cambio.openehr.util.exceptions.InternalErrorException;
import se.cambio.openehr.util.misc.DataValueGenerator;

import java.util.*;

public class ElementInstanceCollectionUtil {


    public static boolean isEmpty(ArchetypeReference ar) {
        for (String idElement : ar.getElementInstancesMap().keySet()) {
            ElementInstance ei = ar.getElementInstancesMap().get(idElement);
            if (ei.getDataValue() != null) {
                return false;
            }
        }
        return true;
    }

    public static ArchetypeReference getEmptyArchetypeReference(Set<ArchetypeReference> archetypeReferences) {
        for (ArchetypeReference archetypeReference : archetypeReferences) {
            if (isEmpty(archetypeReference)) {
                return archetypeReference;
            }
        }
        return null;
    }

    public static boolean containsAll(ArchetypeReference ar1, ArchetypeReference ar2) {
        return ar1.getElementInstancesMap().keySet().containsAll(ar2.getElementInstancesMap().keySet());
    }

    /**
     * Looks if the second reference matches the first one, if empty references are found, they will be copied into the second one (only if the rest is matched).
     *
     * @param ar1
     * @param ar2
     * @param guideMap
     * @param date
     * @return true if ar1 matches ar2
     */
    public static boolean matchAndFill(GeneratedArchetypeReference ar1, ArchetypeReference ar2, Map<String, Guide> guideMap, Calendar date) {
        Collection<ElementInstance> emptyElementInstances = new ArrayList<ElementInstance>();
        boolean matches = matches(ar1, ar2, guideMap, date);
        if (!matches) {
            return false;
        }//else continue with the filling
        //Set AR to empty elementInstances found
        for (String idElement : ar1.getElementInstancesMap().keySet()) {
            ElementInstance ei1 = ar1.getElementInstancesMap().get(idElement);
            ElementInstance ei2 = ar2.getElementInstancesMap().get(idElement);
            if (!(ei1 instanceof PredicateGeneratedElementInstance) && ei2 == null) {
                ei2 = ei1.clone();
                emptyElementInstances.add(ei2);
            }
            if (ei1 instanceof GeneratedElementInstance && ei2 instanceof GeneratedElementInstance) {
                ((GeneratedElementInstance) ei2).getRuleReferences().addAll(((GeneratedElementInstance) ei1).getRuleReferences());
            }
        }
        for (ElementInstance elementInstance : emptyElementInstances) {
            elementInstance.setArchetypeReference(ar2);
        }
        return true;
    }

    public static boolean matches(GeneratedArchetypeReference ar1, ArchetypeReference ar2, Map<String, Guide> guideMap, Calendar date) {
        if (!ar1.getIdArchetype().equals(ar2.getIdArchetype())) {
            return false;
        } else {
            for (String idElement : ar1.getElementInstancesMap().keySet()) {
                ElementInstance ei1 = ar1.getElementInstancesMap().get(idElement);
                ElementInstance ei2 = ar2.getElementInstancesMap().get(idElement);
                if (ei1 instanceof PredicateGeneratedElementInstance) {
                    if (ei2 != null) {
                        OperatorKind operatorKind = ((PredicateGeneratedElementInstance) ei1).getOperatorKind();
                        Set<String> guideIds = new HashSet<>();
                        DataValue dv = getResolveDataValueIfNeeded(guideMap, date, ei1, guideIds);
                        DataValue dv2 = getResolveDataValueIfNeeded(guideMap, date, ei2, guideIds);
                        Collection<Guide> guides = getGuides(guideMap, guideIds);
                        if (!matches(dv, dv2, operatorKind, guides)) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static Collection<Guide> getGuides(Map<String, Guide> guideMap, Set<String> guideIds) {
        Collection<Guide> guides = new ArrayList<>();
        for (String guideId : guideIds) {
            Guide guide = guideMap.get(guideId);
            guides.add(guide);
        }
        return guides;
    }

    private static DataValue getResolveDataValueIfNeeded(Map<String, Guide> guideMap, Calendar date, ElementInstance ei, Set<String> guideIds) {
        DataValue dv = ei.getDataValue();
        if (ei instanceof PredicateGeneratedElementInstance) {
            Set<String> localGuideIds = new HashSet<>();
            PredicateGeneratedElementInstance pgei = ((PredicateGeneratedElementInstance) ei);
            for (RuleReference ruleReference : pgei.getRuleReferences()) {
                Guide guide = guideMap.get(ruleReference.getGuideId());
                if (guide == null) {
                    Logger.getLogger(ElementInstanceCollectionUtil.class).warn("Null guideline for rule reference '" + ruleReference + "'");
                } else {
                    localGuideIds.add(guide.getId());
                }
            }
            Collection<Guide> localGuides = new ArrayList<>();
            for (String guideId : localGuideIds) {
                Guide guide = guideMap.get(guideId);
                localGuides.add(guide);
            }
            if (pgei.getOperatorKind().equals(OperatorKind.IS_A)) {
                dv = ei.getDataValue(); //We do not resolve here IS_A codes, we do that during the dv matching
            } else {
                dv = resolvePredicate(ei.getDataValue(), pgei.getOperatorKind(), localGuides, date);
            }
            guideIds.addAll(localGuideIds);
        }
        return dv;
    }

    public static boolean matches(
            DataValue dv1,
            DataValue dv2,
            OperatorKind operatorKind,
            Collection<Guide> guides) {
        if (OperatorKind.IS_A.equals(operatorKind)) {
            if (dv1 instanceof DvCodedText && dv2 instanceof DvCodedText) {
                CodePhrase elementCodePhrase = ((DvCodedText) dv2).getDefiningCode();
                CodePhrase predicateCodePhrase = ((DvCodedText) dv1).getDefiningCode();
                Set<CodePhrase> resolvedCodePhrases = getResolvedCodePhrases(guides, predicateCodePhrase);
                Set<CodePhrase> resolvedElementCodePhrases = getResolvedCodePhrases(guides, elementCodePhrase);
                if (!resolvedCodePhrases.isEmpty() && !resolvedElementCodePhrases.isEmpty()) {
                    for (CodePhrase resolvedElementCodePhrase : resolvedElementCodePhrases) {
                        try {
                            boolean isSubclass = OpenEHRSessionManager.getTerminologyFacadeDelegate().isSubclassOf(resolvedElementCodePhrase, resolvedCodePhrases);
                            if (!isSubclass) {
                                return false;
                            }
                        } catch (Exception e) {
                            Logger.getLogger(ElementInstanceCollectionUtil.class).warn(e.getMessage());
                            return false;
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        } else if (OperatorKind.EQUALITY.equals(operatorKind)) {
            return DVUtil.equalDVs(dv1, dv2);
        } else if (OperatorKind.INEQUAL.equals(operatorKind)) {
            return !DVUtil.equalDVs(dv1, dv2);
        } else if (OperatorKind.MAX.equals(operatorKind) || (OperatorKind.MIN.equals(operatorKind))) {
            if (dv2 == null) {
                return false;
            } else {
                return true;
            }
        } else if (OperatorKind.GREATER_THAN.equals(operatorKind)) {
            if (dv2 == null) {
                return false;
            } else {
                return DVUtil.compareDVs(dv1, dv2) < 0;
            }
        } else if (OperatorKind.GREATER_THAN_OR_EQUAL.equals(operatorKind)) {
            if (dv2 == null) {
                return false;
            } else {
                return DVUtil.compareDVs(dv1, dv2) <= 0;
            }
        } else if (OperatorKind.LESS_THAN.equals(operatorKind)) {
            if (dv2 == null) {
                return false;
            } else {
                return DVUtil.compareDVs(dv1, dv2) > 0;
            }
        } else if (OperatorKind.LESS_THAN_OR_EQUAL.equals(operatorKind)) {
            if (dv2 == null) {
                return false;
            } else {
                return DVUtil.compareDVs(dv1, dv2) >= 0;
            }
        }
        return false;
    }

    private static Set<CodePhrase> getResolvedCodePhrases(Collection<Guide> guides, CodePhrase predicateCodePhrase) {
        if (!"local".equals(predicateCodePhrase.getTerminologyId().getValue())) {
            return Collections.singleton(predicateCodePhrase); //Already resolved
        }
        Set<CodePhrase> codePhrases = new HashSet<CodePhrase>();
        if (guides != null) {
            for (Guide guide : guides) {
                if (guide.getOntology().getTermBindings() != null) {
                    for (String terminologyId : guide.getOntology().getTermBindings().keySet()) {
                        TermBinding termBinding = guide.getOntology().getTermBindings().get(terminologyId);
                        if (termBinding != null) {
                            Binding binding = termBinding.getBindings().get(predicateCodePhrase.getCodeString());
                            if (binding != null && binding.getCodes() != null) {
                                codePhrases.addAll(binding.getCodes());
                            }
                        }
                    }
                }
            }
        } else {
            codePhrases.add(predicateCodePhrase);
        }
        return codePhrases;
    }

    private static boolean isSameCode(CodePhrase elementCodePhrase, CodePhrase codePhrase) {
        return codePhrase.getCodeString().equals(elementCodePhrase.getCodeString());
    }

    private static boolean isLocalTerminology(CodePhrase elementCodePhrase) {
        return "local".equals(elementCodePhrase.getTerminologyId().getValue());
    }

    public static DataValue resolvePredicate(DataValue dv, OperatorKind op, Collection<Guide> guides, Calendar date) {
        if (OperatorKind.IS_A.equals(op)) {
            if (dv instanceof DvCodedText) {
                return getResolvedCodedText(dv, guides);
            } else {
                Logger.getLogger(ElementInstanceCollectionUtil.class).warn("Not a coded text '" + dv + "'");
                return null;
            }
        } else if (dv instanceof CurrentTimeExpressionDataValue) {
            return getResolvedCurrentDateTime(dv, date);
        } else {
            return dv;
        }
    }

    private static DataValue getResolvedCurrentDateTime(DataValue dv, Calendar date) {
        CurrentTimeExpressionDataValue ctedv = ((CurrentTimeExpressionDataValue) dv);
        ExpressionItem expressionItem = ctedv.getExpressionItem();
        String attribute = ctedv.getAttrbute();
        try {
            String expStr = ExpressionUtil.getArithmeticExpressionStr(null, expressionItem, null);
            date = (date != null ? date : Calendar.getInstance());
            DvDateTime currentDateTime = DataValueGenerator.toDvDateTime(date);
            JexlEngine engine = new JexlEngine();
            Expression e = engine.createExpression(expStr);
            JexlContext context = new MapContext();
            context.set("$" + OpenEHRConst.CURRENT_DATE_TIME_ID, currentDateTime);
            Object obj = e.evaluate(context);
            if (obj instanceof Double) {
                obj = ((Double) obj).longValue(); //In dates we never need double value
            }
            currentDateTime = (DvDateTime) DataValueGenerator.createDV(currentDateTime, attribute, obj);
            return currentDateTime;
        } catch (InternalErrorException e) {
            ExceptionHandler.handle(e);
        }
        return dv;
    }

    private static DataValue getResolvedCodedText(DataValue dv, Collection<Guide> guides) {
        DvCodedText dvCT = (DvCodedText) dv;
        if (guides != null) {
            for (Guide guide : guides) {
                DvCodedText resolvedCodedText = getResolvedCodedText(dvCT, guide);
                if (resolvedCodedText != null) {
                    return resolvedCodedText;
                }
            }
        }
        //If reaches here, no terminology was found (problem)
        String message = "No terminology binding for '" + dv + "' was found! (num guides=" + (guides == null ? "0" : guides.size()) + ")";
        //ExceptionHandler.handle(new InternalErrorException(new Exception(message)));
        Logger.getLogger(ElementInstanceCollectionUtil.class).warn(message);
        return null;
    }

    private static DvCodedText getResolvedCodedText(DvCodedText dvCT, Guide guide) {
        DvCodedText resolvedCodedText = null;
        if (guide.getOntology().getTermBindings() != null) {
            for (String terminologyId : guide.getOntology().getTermBindings().keySet()) {
                TermBinding termBinding = guide.getOntology().getTermBindings().get(terminologyId);
                if (termBinding != null) {
                    Binding binding = termBinding.getBindings().get(dvCT.getDefiningCode().getCodeString());
                    if (binding != null && binding.getCodes() != null && !binding.getCodes().isEmpty()) {
                        CodePhrase cf = binding.getCodes().get(0);
                        List<TermMapping> mappings = new ArrayList<>();
                        for (CodePhrase codePhrase: binding.getCodes()) {
                            mappings.add(new TermMapping(codePhrase, Match.EQUIVALENT, null, null));
                        }
                        resolvedCodedText = new DvCodedText(dvCT.getValue(), mappings, null, null, null, null, cf, null);
                    }
                }
            }
        }
        return resolvedCodedText;
    }
}
/*
 *  ***** BEGIN LICENSE BLOCK *****
 *  Version: MPL 2.0/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  2.0 (the 'License'); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an 'AS IS' basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *
 *  The Initial Developers of the Original Code are Iago Corbal and Rong Chen.
 *  Portions created by the Initial Developer are Copyright (C) 2012-2013
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *
 * Software distributed under the License is distributed on an 'AS IS' basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 *  ***** END LICENSE BLOCK *****
 */