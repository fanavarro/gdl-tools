package se.cambio.cds.gdl.converters.drools;

import org.apache.log4j.Logger;
import org.openehr.rm.datatypes.basic.DataValue;
import org.openehr.rm.datatypes.text.CodePhrase;
import se.cambio.cds.controller.execution.DroolsExecutionManager;
import se.cambio.cds.gdl.model.ArchetypeBinding;
import se.cambio.cds.gdl.model.Binding;
import se.cambio.cds.gdl.model.ElementBinding;
import se.cambio.cds.gdl.model.Guide;
import se.cambio.cds.gdl.model.Rule;
import se.cambio.cds.gdl.model.TermBinding;
import se.cambio.cds.gdl.model.expression.AssignmentExpression;
import se.cambio.cds.gdl.model.expression.BinaryExpression;
import se.cambio.cds.gdl.model.expression.ConstantExpression;
import se.cambio.cds.gdl.model.expression.CreateInstanceExpression;
import se.cambio.cds.gdl.model.expression.ExpressionItem;
import se.cambio.cds.gdl.model.expression.MultipleAssignmentExpression;
import se.cambio.cds.gdl.model.expression.OperatorKind;
import se.cambio.cds.gdl.model.expression.UnaryExpression;
import se.cambio.cds.gdl.model.expression.Variable;
import se.cambio.cds.model.instance.ArchetypeReference;
import se.cambio.cds.util.ExpressionUtil;
import se.cambio.cds.util.RefStat;
import se.cambio.cds.util.export.DVDefSerializer;
import se.cambio.cm.model.archetype.vo.ArchetypeElementVO;
import se.cambio.openehr.controller.session.data.ArchetypeElements;
import se.cambio.openehr.controller.session.data.ArchetypeManager;
import se.cambio.openehr.util.OpenEHRConst;
import se.cambio.openehr.util.OpenEHRDataValues;
import se.cambio.openehr.util.exceptions.InternalErrorException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GDLDroolsConverter {


    private static Logger log = Logger.getLogger(GDLDroolsConverter.class);
    private final ArchetypeManager archetypeManager;
    private Guide guide;

    // Drools keywords
    private static final String RULE = "rule";
    private static final String WHEN = "when";
    private static final String THEN = "then";
    private static final String END = "end";
    private static final String DEFAULT_CONFIG = "no-loop true";
    private static final String AGENDA_GROUP = "agenda-group";
    private static final String SALIENCE = "salience";
    private static final String TAB = "      ";
    private static final String AGENDA_GROUP_LINK_ID = "*agenda-group-link";
    private static final String DEFAULT_RULE_CODE = "default";
    private Map<String, String> _gtElementToWholeDefinition = new HashMap<String, String>();
    private Map<String, String> _gtElementToDefinition = new HashMap<String, String>();
    private Map<String, String> _gtElementToElementId = new HashMap<String, String>();
    private int creationIndex = 0;
    private Map<String, ArchetypeReference> archetypeReferenceMap;
    private Map<String, ArchetypeElementVO> elementMap;
    private Map<String, String> archetypeBindingGtCodeToDefinition;
    private Map<String, String> gtElementToArchetypeBindingGtCode;
    private Map<RefStat, Set<String>> preconditionStats;
    private StringBuffer sb;
    private int predicateCount;

    public GDLDroolsConverter(Guide guide, ArchetypeManager archetypeManager) {
        this.guide = guide;
        this.archetypeManager = archetypeManager;
        init();
    }

    private void init() {
        archetypeReferenceMap = new HashMap<String, ArchetypeReference>();
        elementMap = new HashMap<String, ArchetypeElementVO>();
        // Add currentTime
        elementMap.put(OpenEHRConst.CURRENT_DATE_TIME_ID,
                ArchetypeElements.CURRENT_DATE_TIME);
        archetypeBindingGtCodeToDefinition = new HashMap<String, String>();
        gtElementToArchetypeBindingGtCode = new HashMap<String, String>();
        preconditionStats = initStats();
        predicateCount = 0;
        sb = new StringBuffer();
    }

    public String convertToDrools() throws InternalErrorException{
        createHeader();
        fillDefinitions();
        insertRules();
        insertDefaultActionsRule();
        return sb.toString();
    }

    private void createHeader() {
        String guideHeader = getGuideHeader();
        sb.append(guideHeader);
    }

    private String getPreconditionString() throws InternalErrorException {
        String preconditionStr = null;
        if (guide.getDefinition().getPreConditionExpressions() != null) {
            preconditionStr = convertExpressionsToMVEL(
                    guide.getDefinition().getPreConditionExpressions(),
                    archetypeReferenceMap,
                    elementMap,
                    preconditionStats);
        }
        return preconditionStr;
    }

    private void insertDefaultActionsRule() throws InternalErrorException {
        List<AssignmentExpression> defaultActions = guide.getDefinition().getDefaultActionExpressions();
        if (!defaultActions.isEmpty()) {
            Map<RefStat, Set<String>> ruleStats = initStats();
            String defaultActionsStr = convertAssignmentExpressionsToMVEL(defaultActions, ruleStats);
            String definition = getDefinitionForRule(ruleStats);
            sb.append(RULE + " \"" + guide.getId() + "/" + DEFAULT_RULE_CODE + "\"\n");
            String guideSalienceId = DroolsExecutionManager.getGuideSalienceId(guide.getId());
            sb.append(SALIENCE + " "+ guideSalienceId + " + 9999\n");
            sb.append(DEFAULT_CONFIG + "\n");
            sb.append(WHEN + "\n");
            if (definition != null) {
                sb.append(definition);
            }
            appendFiredRuleCondition(sb, true, DEFAULT_RULE_CODE);
            sb.append(THEN + "\n");
            if (definition != null) {
                sb.append(defaultActionsStr);
            }
            sb.append(getFiredRuleWMInsertion(DEFAULT_RULE_CODE));
            sb.append(END + "\n\n");
        }
    }

    private void insertRules() throws InternalErrorException {
        String preconditionStr = getPreconditionString();
        for (Rule rule : guide.getDefinition().getRules().values()) {
            Map<RefStat, Set<String>> ruleStats = initStats();
            String whenStr = convertExpressionsToMVEL(rule.getWhenStatements(),
                    archetypeReferenceMap,
                    elementMap, ruleStats);
            String thenStr = convertAssignmentExpressionsToMVEL(rule.getThenStatements(), ruleStats);
            String definition = getDefinitionForRule(ruleStats);
            ruleStats.get(RefStat.ATT_SET_REF).remove(OpenEHRConst.CURRENT_DATE_TIME_ID);
            String hasValueChecks =
                    getHasValueStr(ruleStats.get(RefStat.ATT_SET_REF));
            //Check if a function is used, add whatever extra code necessary for it (for now, just count)
            Set<String> functionsRefs = new HashSet<String>();
            functionsRefs.addAll(ruleStats.get(RefStat.ATT_FUNCTIONS));
            functionsRefs.addAll(preconditionStats.get(RefStat.ATT_FUNCTIONS));
            String functionExtraCode = getFunctionsExtraCode(functionsRefs);
            sb.append(RULE + " \"" + guide.getId() + "/" + rule.getId() + "\"\n");
            String guideSalienceId = DroolsExecutionManager.getGuideSalienceId(guide.getId());
            sb.append(SALIENCE + " "+ guideSalienceId + " + " + rule.getPriority() + "\n");
            sb.append(DEFAULT_CONFIG + "\n");
            sb.append(WHEN + "\n");
            if (definition != null){
                sb.append(definition);
            }
            if (functionExtraCode != null){
                sb.append(functionExtraCode);
            }
            if (hasValueChecks != null){
                sb.append(hasValueChecks);
            }
            if (preconditionStr != null){
                sb.append(preconditionStr);
            }
            if (whenStr != null){
                sb.append(whenStr);
            }
            sb.append(THEN + "\n");
            if (thenStr != null){
                sb.append(thenStr);
            }
            sb.append(getFiredRuleWMInsertion(rule.getId()));
            sb.append(END + "\n\n");
        }
    }

    private String getFiredRuleWMInsertion(String ruleGtCode) {
        return TAB + "insert(new FiredRuleReference(\"" + guide.getId() + "\", \"" + ruleGtCode + "\"));\n";

    }

    private void fillDefinitions() throws InternalErrorException {
        String arID = "archetypeReference";
        for(ArchetypeBinding archetypeBinding: guide.getDefinition().getArchetypeBindings().values()){
            StringBuffer archetypeBindingMVELSB = new StringBuffer();
            String gtCodeArchetypeReference = archetypeBinding.getId();
            archetypeBindingMVELSB.append(TAB);
            archetypeBindingMVELSB.append("$" + arID + "_" + gtCodeArchetypeReference);
            String idDomain = archetypeBinding.getDomain();
            archetypeBindingMVELSB.append(":ArchetypeReference");
            archetypeBindingMVELSB.append("(");
            if (idDomain != null){
                archetypeBindingMVELSB.append("idDomain==\"" + idDomain + "\", ");
            }
            String archetypeId = archetypeBinding.getArchetypeId();
            String templateId = archetypeBinding.getTemplateId();
            archetypeBindingMVELSB.append("idArchetype==\"" + archetypeId + "\"");
            archetypeBindingMVELSB.append(")\n");
            archetypeReferenceMap.put(gtCodeArchetypeReference, new ArchetypeReference(idDomain, archetypeId, templateId));
            processPredicates(gtCodeArchetypeReference, arID, archetypeBinding, archetypeBindingMVELSB);
            archetypeBindingGtCodeToDefinition.put(gtCodeArchetypeReference, archetypeBindingMVELSB.toString());
            processElementBindings(archetypeBinding.getId(), arID, archetypeBinding);
        }
    }

    private void processElementBindings(String archetypeReferenceGTCode, String arID, ArchetypeBinding archetypeBinding) {
        Map<String, ElementBinding> elementBindingsMap = archetypeBinding.getElements();
        if (elementBindingsMap!=null) {
            for (ElementBinding element : elementBindingsMap.values()) {
                StringBuffer elementDefinitionSB = new StringBuffer();
                String idElement = archetypeBinding.getArchetypeId() + element.getPath();
                ArchetypeElementVO value = archetypeManager.getArchetypeElements().getArchetypeElement(
                        archetypeBinding.getTemplateId(), idElement);
                elementMap.put(element.getId(), value);
                elementDefinitionSB.append("ElementInstance(id==\"" + idElement + "\", archetypeReference==$" + arID + "_" + archetypeReferenceGTCode + ")");
                _gtElementToDefinition.put(element.getId(), elementDefinitionSB.toString());
                _gtElementToElementId.put(element.getId(), idElement);
                gtElementToArchetypeBindingGtCode.put(element.getId(), archetypeReferenceGTCode);
            }
        }
    }

    private void processPredicates(String gtCodeArchetypeReference, String arID, ArchetypeBinding archetypeBinding, StringBuffer archetypeBindingMVELSB) throws InternalErrorException {
        // Predicates
        if (archetypeBinding.getPredicateStatements() != null) {
            for (ExpressionItem expressionItem : archetypeBinding.getPredicateStatements()) {
                if (expressionItem instanceof BinaryExpression) {
                    BinaryExpression binaryExpression = (BinaryExpression) expressionItem;
                    processBinaryExpressionPredicate(gtCodeArchetypeReference, arID, archetypeBinding, archetypeBindingMVELSB, binaryExpression);
                }else if (expressionItem instanceof UnaryExpression) {
                    UnaryExpression unaryExpression = (UnaryExpression) expressionItem;
                    processUnaryExpressionPredicate(gtCodeArchetypeReference, arID, archetypeBinding, archetypeBindingMVELSB, unaryExpression);
                }
            }
        }
    }

    private void processUnaryExpressionPredicate(String gtCodeArchetypeReference, String arID, ArchetypeBinding archetypeBinding, StringBuffer archetypeBindingMVELSB, UnaryExpression unaryExpression) throws InternalErrorException {
        predicateCount++;
        Variable variable = (Variable) unaryExpression.getOperand();
        String idElement = archetypeBinding
                .getArchetypeId() + variable.getPath();
        archetypeBindingMVELSB.append(TAB);
        archetypeBindingMVELSB
                .append("ElementInstance(id==\""
                        + idElement
                        + "\", archetypeReference==$"
                        + arID + "_" +  gtCodeArchetypeReference + ", "
                        + "predicate || dataValue instanceof Comparable, "
                        + "$predDV" + predicateCount + ":dataValue"
                        + ")\n");
        ArchetypeElementVO archetypeElement = archetypeManager.getArchetypeElements().getArchetypeElement(
                archetypeBinding.getTemplateId(),
                idElement);
        OperatorKind op = unaryExpression.getOperator();
        String opStr = null;
        if (OperatorKind.MAX.equals(op)){
            opStr=">";
        }else if (OperatorKind.MIN.equals(op)){
            opStr="<";
        }else{
            Logger.getLogger(GDLDroolsConverter.class).warn("Guide="+guide.getId()+", Operator for predicate '"+op+"' is not valid.");
        }
        if (archetypeElement != null && opStr != null){
            String predAuxDef = getComparisonPredicateChecks(archetypeBinding, predicateCount);
            String predicateArchetypeRef = "";
            archetypeBindingMVELSB.append(TAB);
            archetypeBindingMVELSB.append("not(");
            if (predAuxDef != null){
                archetypeBindingMVELSB.append(predAuxDef);
                predicateArchetypeRef = "archetypeReference==$archetypeReferencePredicate"+predicateCount+",";
            }
            archetypeBindingMVELSB.append(TAB);
            archetypeBindingMVELSB.append("ElementInstance(id==\""
                    + idElement + "\", "
                    + predicateArchetypeRef
                    + "DVUtil.areDomainsCompatible($" + arID + "_" + gtCodeArchetypeReference + ".getIdDomain(), archetypeReference.getIdDomain()),"
                    + "DVUtil.checkMaxMin($predDV" + predicateCount + ", dataValue, \"" + op.getSymbol() + "\")"
                    +"))\n");
        }else{
            throw new InternalErrorException(new Exception("Element not found '"+idElement+"'"));
        }
    }

    private void processBinaryExpressionPredicate(String gtCodeArchetypeReference, String arID, ArchetypeBinding archetypeBinding, StringBuffer archetypeBindingMVELSB, BinaryExpression binaryExpression) throws InternalErrorException {
        if (binaryExpression.getLeft() instanceof Variable){
            predicateCount++;
            Variable variable = (Variable) binaryExpression
                    .getLeft();

            if (binaryExpression.getRight() instanceof ConstantExpression) {
                ConstantExpression constantExpression = (ConstantExpression) binaryExpression.getRight();
                String idElement = archetypeBinding
                        .getArchetypeId() + variable.getPath();
                archetypeBindingMVELSB.append(TAB);
                archetypeBindingMVELSB.append("$predicate"
                        + predicateCount);
                archetypeBindingMVELSB.append(":ElementInstance(id==\""
                        + idElement
                        + "\", archetypeReference==$"
                        + arID + "_" + gtCodeArchetypeReference + ")\n");
                ArchetypeElementVO archetypeElement =
                        archetypeManager.getArchetypeElements().getArchetypeElement(
                                archetypeBinding.getTemplateId(),
                                idElement);
                if (archetypeElement == null) {
                    throw new InternalErrorException(new Exception("Element not found '"+idElement+"'"+(archetypeBinding.getTemplateId()!=null?"("+archetypeBinding.getTemplateId()+")":"")));
                }
                String rmType = archetypeElement.getRMType();
                archetypeBindingMVELSB.append(TAB);
                String dvStr = "null";
                if (!constantExpression.getValue().equals("null")) {
                    dvStr = DVDefSerializer.getDVInstantiation(DataValue.parseValue(rmType + "," + constantExpression.getValue()));
                }
                archetypeBindingMVELSB
                        .append("eval(" +
                                getOperatorMVELLine(
                                        "$predicate" + predicateCount,
                                        binaryExpression.getOperator(),
                                        dvStr,
                                        true) +
                                ")\n");

            }else if (binaryExpression.getRight() instanceof ExpressionItem) {
                String path = variable.getPath();
                String attribute = path.substring(path.lastIndexOf("/value/")+7, path.length());
                path = path.substring(0, path.length()-attribute.length()-7);
                String idElement = archetypeBinding
                        .getArchetypeId() + path;
                archetypeBindingMVELSB.append(TAB);
                String predicateHandle = "predicate"+ predicateCount;
                archetypeBindingMVELSB.append("$"+predicateHandle);
                archetypeBindingMVELSB
                        .append(":ElementInstance(id==\""
                                + idElement + "\", "
                                + "dataValue!=null, "
                                + "archetypeReference==$"
                                + arID + "_" + gtCodeArchetypeReference + ")\n");
                ArchetypeElementVO archetypeElement = archetypeManager.getArchetypeElements().getArchetypeElement(
                        archetypeBinding.getTemplateId(),
                        idElement);
                if (archetypeElement != null) {
                    String rmName = archetypeElement.getRMType();
                    String arithmeticExpStr = //We cast it to long because all elements from CurrentTime fit into this class, but we must make it more generic (TODO)
                            "((long)"+ ExpressionUtil.getArithmeticExpressionStr(elementMap, binaryExpression.getRight(), null)+")";
                    archetypeBindingMVELSB.append(TAB);
                    archetypeBindingMVELSB.append("eval(");
                    Variable var = new Variable(predicateHandle, predicateHandle, path, attribute);
                    String varCall = ExpressionUtil.getVariableWithAttributeStr(rmName,var);
                    archetypeBindingMVELSB.append("(");
                    if (isString(rmName, attribute)){
                        archetypeBindingMVELSB.append(getAttributeOperatorMVELLine(varCall, binaryExpression.getOperator(), arithmeticExpStr));
                    }else{
                        archetypeBindingMVELSB.append(varCall);
                        archetypeBindingMVELSB.append(binaryExpression.getOperator().getSymbol());
                        archetypeBindingMVELSB.append(arithmeticExpStr);
                    }
                    archetypeBindingMVELSB.append("))\n");
                }else{
                    throw new InternalErrorException(new Exception("Element not found '"+idElement+"'"+(archetypeBinding.getTemplateId()!=null?"("+archetypeBinding.getTemplateId()+")":"")));
                }
            }
        }
    }


    private String getComparisonPredicateChecks(ArchetypeBinding archetypeBinding, Integer predicateCount){
        StringBuffer sb = new StringBuffer();
        for (ExpressionItem expressionItem : archetypeBinding.getPredicateStatements()) {
            if (expressionItem instanceof BinaryExpression) {
                BinaryExpression binaryExpression = (BinaryExpression) expressionItem;
                if (binaryExpression.getLeft() instanceof Variable
                        && binaryExpression.getRight() instanceof ConstantExpression) {
                    Variable variable = (Variable) binaryExpression.getLeft();
                    ConstantExpression constantExpression = (ConstantExpression) binaryExpression.getRight();
                    String idElement = archetypeBinding.getArchetypeId() + variable.getPath();
                    sb.append(TAB);
                    sb.append("$predicateAux"+ predicateCount);
                    sb.append(":ElementInstance(id==\"");
                    sb.append(idElement);
                    sb.append("\", archetypeReference==$archetypeReferencePredicate" + predicateCount + ") and \n");
                    ArchetypeElementVO archetypeElement = archetypeManager.getArchetypeElements().getArchetypeElement(
                            archetypeBinding.getTemplateId(),
                            idElement);
                    if (archetypeElement!=null){
                        String rmType = archetypeElement.getRMType();
                        String dvStr = "null";
                        if (!"null".equals(constantExpression.getValue())){
                            dvStr = DVDefSerializer.getDVInstantiation(DataValue.parseValue(rmType+ ","+ constantExpression.getValue()));
                        }
                        sb.append(TAB);
                        sb.append("eval("+
                                getOperatorMVELLine(
                                        "$predicateAux"+ predicateCount,
                                        binaryExpression.getOperator(),
                                        dvStr,
                                        true)+
                                ") and \n");
                    }
                }
            }
        }
        String predicateDef = sb.toString();
        if (!predicateDef.isEmpty()) {
            sb = new StringBuffer();
            sb.append(TAB);
            sb.append("$archetypeReferencePredicate" + predicateCount + ":ArchetypeReference(idDomain==\"EHR\",");
            sb.append("idArchetype==\"" + archetypeBinding.getArchetypeId() + "\") and \n");
            return sb.toString() + predicateDef;
        }else{
            return null;
        }
    }


    private String getDefinitionForRule(Map<RefStat, Set<String>> ruleStats) {
        Set<String> gtCodesRef = new HashSet<String>();
        gtCodesRef.addAll(ruleStats.get(RefStat.REFERENCE));
        gtCodesRef.addAll(preconditionStats.get(RefStat.REFERENCE));
        gtCodesRef.remove(OpenEHRConst.CURRENT_DATE_TIME_ID);
        Map<String, StringBuffer> archetypeDefinitions = new HashMap<String, StringBuffer>();
        for (String elementGtCode : gtCodesRef) {
            String gtCodeArchetypeBinding = gtElementToArchetypeBindingGtCode.get(elementGtCode);
            if (gtCodeArchetypeBinding != null) {
                StringBuffer definition = archetypeDefinitions.get(gtCodeArchetypeBinding);
                if (definition == null) {
                    definition = new StringBuffer();
                    definition.append(archetypeBindingGtCodeToDefinition.get(gtCodeArchetypeBinding));
                    archetypeDefinitions.put(gtCodeArchetypeBinding, definition);
                }
                definition.append(TAB);
                definition.append("$" + elementGtCode + ":" + _gtElementToDefinition.get(elementGtCode) + "\n");
            }
        }
        StringBuffer resultSB = new StringBuffer();
        for (StringBuffer definition : archetypeDefinitions.values()) {
            resultSB.append(definition.toString());
        }

        for (String elementGtCode: gtCodesRef){
            String gtCodeArchetypeBinding = gtElementToArchetypeBindingGtCode.get(elementGtCode);
            if (gtCodeArchetypeBinding != null) {
                String archetypeDefinition = archetypeDefinitions.get(gtCodeArchetypeBinding).toString();
                _gtElementToWholeDefinition.put(elementGtCode, archetypeDefinition);
            } else {
                String firedRuleDefinition = "$" + elementGtCode + ":FiredRuleReference(guideId==\"" + guide.getId() + "\", gtCode==\"" + elementGtCode + "\")";
                _gtElementToWholeDefinition.put(elementGtCode, firedRuleDefinition);
            }
        }

        return resultSB.toString();
    }

    private Map<RefStat, Set<String>> initStats() {
        Map<RefStat, Set<String>> stats = new HashMap<RefStat, Set<String>>();
        for (RefStat refStat : RefStat.values()) {
            stats.put(refStat, new HashSet<String>());
        }
        return stats;
    }

    private String convertExpressionsToMVEL(
            Collection<ExpressionItem> expressionItems,
            Map<String, ArchetypeReference> archetypeReferenceMap,
            Map<String, ArchetypeElementVO> elementMap,
            Map<RefStat, Set<String>> stats) throws InternalErrorException {
        StringBuffer sb = new StringBuffer();
        if (expressionItems != null) {
            for (ExpressionItem expressionItem : expressionItems) {
                sb.append(TAB);
                processExpressionItem(sb, expressionItem, archetypeReferenceMap, elementMap, stats);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String convertAssignmentExpressionsToMVEL(
            Collection<AssignmentExpression> expressionItems,
            Map<RefStat, Set<String>> stats) throws InternalErrorException {
        StringBuffer sb = new StringBuffer();
        if (expressionItems != null) {
            for (ExpressionItem expressionItem : expressionItems) {
                sb.append(TAB);
                processExpressionItem(sb, expressionItem, archetypeReferenceMap, elementMap, stats);
                sb.append("\n");
            }
            for (String gtCodes : stats.get(RefStat.SET)) {
                sb.append(TAB);
                sb.append("modify($"+gtCodes+"){};\n");
            }
        }
        return sb.toString();
    }

    protected void processExpressionItem(StringBuffer sb,
                                         ExpressionItem expressionItem,
                                         Map<String, ArchetypeReference> archetypeReferenceMap,
                                         Map<String, ArchetypeElementVO> elementMap,
                                         Map<RefStat, Set<String>> stats) throws InternalErrorException {
        if (expressionItem instanceof AssignmentExpression) {
            processAssignmentExpression(sb,
                    (AssignmentExpression) expressionItem, archetypeReferenceMap, elementMap, stats, false);
        } else if (expressionItem instanceof BinaryExpression) {
            processBinaryExpression(sb, (BinaryExpression) expressionItem, archetypeReferenceMap,
                    elementMap, stats);
        } else if (expressionItem instanceof UnaryExpression) {
            processUnaryExpression(sb, (UnaryExpression) expressionItem, archetypeReferenceMap,
                    elementMap, stats);
        } else {
            throw new InternalErrorException(new Exception("Unknown expression '"+ expressionItem.getClass().getName() + "'"));
        }
    }

    protected void processAssignmentExpression(StringBuffer sb,
                                               AssignmentExpression assignmentExpression,
                                               Map<String, ArchetypeReference> archetypeReferenceMap,
                                               Map<String, ArchetypeElementVO> elementMap,
                                               Map<RefStat, Set<String>> stats,
                                               boolean creatingInstance) throws InternalErrorException {
        String gtCode = assignmentExpression.getVariable().getCode();
        Variable var = assignmentExpression.getVariable();
        String attribute = var.getAttribute();
        processAssignmentExpression(sb, gtCode, gtCode, attribute, assignmentExpression.getAssignment(), archetypeReferenceMap, elementMap, stats, creatingInstance);
    }

    protected void processAssignmentExpression(StringBuffer sb,
                                               String gtCode,
                                               String eiId,
                                               String attribute,
                                               ExpressionItem expressionItemAux,
                                               Map<String, ArchetypeReference> archetypeReferenceMap,
                                               Map<String, ArchetypeElementVO> elementMap,
                                               Map<RefStat, Set<String>> stats,
                                               boolean creatingInstance) throws InternalErrorException {
        if (!CreateInstanceExpression.FUNCTION_CREATE_NAME.equals(attribute) && !creatingInstance){
            stats.get(RefStat.REFERENCE).add(gtCode);
            stats.get(RefStat.SET).add(gtCode);
        }
        if (attribute == null) {
            if (expressionItemAux instanceof Variable) {
                Variable var2 = (Variable) expressionItemAux;
                String gtCodeAux = var2.getCode();
                stats.get(RefStat.REFERENCE).add(gtCodeAux);
                sb.append("$"+eiId+".setDataValue($"+gtCodeAux+ExpressionUtil.getDataValueMethod(gtCode)+");");
                sb.append("$"+eiId+".setNullFlavour(null);");
                sb.append("$executionLogger.addLog(drools, $"+ eiId + ");");
            } else if (expressionItemAux instanceof ConstantExpression) {
                String dvStr =
                        ((ConstantExpression) expressionItemAux).getValue();
                ArchetypeElementVO archetypeElementVO = elementMap.get(gtCode);
                if (archetypeElementVO==null){
                    throw new InternalErrorException(new Exception("Guide="+guide.getId()+", Unknown element for gtCode '"+gtCode+"'"));
                }
                String rmType = archetypeElementVO.getRMType();
                DataValue dv = DataValue.parseValue(rmType + "," + dvStr);
                sb.append("$"+eiId+".setDataValue("+ DVDefSerializer.getDVInstantiation(dv)+");");
                sb.append("$"+eiId+".setNullFlavour(null);");
                sb.append("$executionLogger.addLog(drools, $"+eiId +");");
            } else {
                throw new InternalErrorException(new Exception("Guide="+guide.getId()+", Unknown expression '"+expressionItemAux+"'"));
            }
        } else {
            if (attribute.equals(OpenEHRConst.NULL_FLAVOR_ATTRIBUTE)){
                String dvStr = ((ConstantExpression) expressionItemAux).getValue();
                DataValue dv = DataValue.parseValue(OpenEHRDataValues.DV_CODED_TEXT + "," + dvStr);
                //Map<RefStat, Set<String>> statsAux = initStats(); //????
                //stats.get(RefStat.REFERENCE).addAll(statsAux.get(RefStat.REFERENCE));  //????
                sb.append("$"+eiId+ ".setDataValue(null);");
                sb.append("$"+eiId+".setNullFlavour("+ DVDefSerializer.getDVInstantiation(dv)+");");
                sb.append("$executionLogger.addLog(drools, $"+eiId +");");
            }else if (attribute.equals(CreateInstanceExpression.FUNCTION_CREATE_NAME)) {
                ArchetypeReference ar = archetypeReferenceMap.get(gtCode);
                String arId = "newAR"+creationIndex;
                sb.append("ArchetypeReference " + arId + " = new ArchetypeReference(\"CDS\", \"" + ar.getIdArchetype() + "\"," + (ar.getIdTemplate() != null ? "\"" + ar.getIdTemplate() + "\"" : "null") + ");\n");
                sb.append(TAB);
                sb.append("insert("+arId+");\n");
                insertAssignments(sb, arId, archetypeReferenceMap, elementMap, expressionItemAux, stats);
                creationIndex++;
            }else{
                ArchetypeElementVO archetypeElementVO = elementMap.get(gtCode);
                if (archetypeElementVO==null){
                    throw new InternalErrorException(new Exception("GTCode '"+gtCode+"' not found. (guideId='"+guide.getId()+"')"));
                }
                String rmName = archetypeElementVO.getRMType();
                Map<RefStat, Set<String>> statsAux = initStats();
                String arithmeticExpStr =
                        ExpressionUtil.getArithmeticExpressionStr(elementMap, expressionItemAux, statsAux);
                stats.get(RefStat.REFERENCE).addAll(statsAux.get(RefStat.REFERENCE));
                stats.get(RefStat.REFERENCE).addAll(statsAux.get(RefStat.ATT_FUNCTIONS_REF));
                stats.get(RefStat.ATT_SET_REF).addAll(statsAux.get(RefStat.REFERENCE));
                stats.get(RefStat.ATT_FUNCTIONS).addAll(statsAux.get(RefStat.ATT_FUNCTIONS));
                sb.append("$"+ eiId+ "."+ getAttributeSettingStr(eiId, rmName, attribute, arithmeticExpStr)+ ";");
                sb.append("$"+ eiId+ ".setNullFlavour(null);");
                sb.append("$executionLogger.addLog(drools, $"+ eiId + ");");
            }
        }
    }

    private void insertAssignments(
            StringBuffer sb, String arId,
            Map<String, ArchetypeReference> archetypeReferenceMap,
            Map<String, ArchetypeElementVO> elementMap,
            ExpressionItem expressionItem,
            Map<RefStat, Set<String>> stats) throws InternalErrorException {
        if (!(expressionItem instanceof MultipleAssignmentExpression)){
            throw new InternalErrorException(new Exception("Guide=" + guide.getId() + ", Incorrect expression inside creation expression '" + expressionItem + "'"));
        }
        MultipleAssignmentExpression multipleAssignmentExpression = (MultipleAssignmentExpression)expressionItem;
        int i = 0;
        Map<String, String> elementIdsMap = new HashMap<String, String>();
        for(AssignmentExpression assignmentExpressionAux: multipleAssignmentExpression.getAssignmentExpressions()){
            String gtCode = assignmentExpressionAux.getVariable().getCode();
            ArchetypeElementVO archetypeElementVO = elementMap.get(gtCode);
            if (archetypeElementVO==null){
                throw new InternalErrorException(new Exception("GTCode '"+gtCode+"' not found. (guideId='"+guide.getId()+"')"));
            }
            String elementId = archetypeElementVO.getId();
            String eiId = elementIdsMap.get(elementId);
            if (eiId==null){
                eiId = "ei"+creationIndex+"_"+i;
                elementIdsMap.put(elementId, eiId);
                sb.append(TAB);
                sb.append("ElementInstance $"+eiId+" = new ElementInstance(\""+elementId+"\", null, "+arId+", null, null);\n");
            }else{
                sb.append("\n");
            }
            sb.append(TAB);
            String attribute = assignmentExpressionAux.getVariable().getAttribute();
            processAssignmentExpression(sb, gtCode, eiId, attribute, assignmentExpressionAux.getAssignment(), archetypeReferenceMap, elementMap, stats, true);
            sb.append("insert($" + eiId + ");");
            i++;
        }
    }

    private String getAttributeSettingStr(String gtCode, String rmName,
                                          String attributeName, String setStr) {
        return "setDataValue(DVUtil.createDV($" + gtCode + ",\"" + rmName
                + "\",\"" + attributeName + "\"," + setStr + "))";
    }

    protected void processBinaryExpression(StringBuffer sb,
                                           BinaryExpression binaryExpression,
                                           Map<String, ArchetypeReference> archetypeReferenceMap,
                                           Map<String, ArchetypeElementVO> elementMap,
                                           Map<RefStat, Set<String>> stats) throws InternalErrorException {
        if (OperatorKind.OR.equals(binaryExpression.getOperator())) {
            sb.append("(");
            processExpressionItem(sb, binaryExpression.getLeft(), archetypeReferenceMap, elementMap,
                    stats);
            sb.append(" or ");
            processExpressionItem(sb, binaryExpression.getRight(), archetypeReferenceMap, elementMap,
                    stats);
            sb.append(")");
        } else if (OperatorKind.AND.equals(binaryExpression.getOperator())) {
            sb.append("(");
            processExpressionItem(sb, binaryExpression.getLeft(), archetypeReferenceMap, elementMap,
                    stats);
            sb.append(" and ");
            processExpressionItem(sb, binaryExpression.getRight(), archetypeReferenceMap, elementMap,
                    stats);
            sb.append(")");
        } else if (OperatorKind.EQUALITY.equals(binaryExpression.getOperator())
                || OperatorKind.INEQUAL.equals(binaryExpression.getOperator())
                || OperatorKind.IS_A.equals(binaryExpression.getOperator())
                || OperatorKind.IS_NOT_A.equals(binaryExpression.getOperator())
                || OperatorKind.GREATER_THAN.equals(binaryExpression.getOperator())
                || OperatorKind.GREATER_THAN_OR_EQUAL.equals(binaryExpression.getOperator())
                || OperatorKind.LESS_THAN.equals(binaryExpression.getOperator())
                || OperatorKind.LESS_THAN_OR_EQUAL.equals(binaryExpression.getOperator())) {
            processComparisonExpression(sb, binaryExpression, elementMap, stats);
        } else {
            throw new InternalErrorException(new Exception("Unknown operator '"
                    + binaryExpression.getOperator() + "'"));
        }

    }

    protected void processUnaryExpression(StringBuffer sb,
                                          UnaryExpression unaryExpression,
                                          Map<String, ArchetypeReference> archetypeReferenceMap,
                                          Map<String, ArchetypeElementVO> elementMap,
                                          Map<RefStat, Set<String>> stats) throws InternalErrorException {
        if (OperatorKind.NOT.equals(unaryExpression.getOperator())) {
            sb.append("not(");
            processExpressionItem(sb, unaryExpression.getOperand(), archetypeReferenceMap, elementMap, stats);
            sb.append(")");
        } else if (OperatorKind.FOR_ALL.equals(unaryExpression.getOperator())) {
            sb.append("forall(");
            processExpressionItem(sb, unaryExpression.getOperand(), archetypeReferenceMap,elementMap,
                    stats);
            sb.append(")");
        } else if (OperatorKind.FIRED.equals(unaryExpression.getOperator()) ||
                OperatorKind.NOT_FIRED.equals(unaryExpression.getOperator())) {
            if (!(unaryExpression.getOperand() instanceof Variable)) {
                throw new CompilationErrorException("Expected variable inside fired() operation. Instead got '" + unaryExpression.getOperand().getClass().getSimpleName() + "'");
            }
            boolean negated = OperatorKind.NOT_FIRED.equals(unaryExpression.getOperator());
            String gtCode = ((Variable)unaryExpression.getOperand()).getCode();
            appendFiredRuleCondition(sb, negated, gtCode);
        } else {
            throw new InternalErrorException(new Exception(
                    "Unknown operator '" + unaryExpression.getOperator() + "'"));
        }
    }

    private void appendFiredRuleCondition(StringBuffer sb, boolean negated, String gtCode) {
        sb.append(TAB);
        if (negated) {
            sb.append("not(");
        }
        sb.append("FiredRuleReference(guideId == \"");
        sb.append(guide.getId());
        sb.append("\", gtCode == \"");
        sb.append(gtCode);
        sb.append("\")");
        if (negated) {
            sb.append(")");
        }
        sb.append("\n");
    }

    protected void processComparisonExpression(StringBuffer sb,
                                               BinaryExpression binaryExpression,
                                               Map<String, ArchetypeElementVO> elementMap,
                                               Map<RefStat, Set<String>> stats) throws InternalErrorException {
        Variable var = null;
        if (binaryExpression.getLeft() instanceof Variable) {
            var = (Variable) binaryExpression.getLeft();
            stats.get(RefStat.REFERENCE).add(var.getCode());
        }
        if (var != null) {
            ArchetypeElementVO archetypeElementVO = elementMap.get(var.getCode());
            if (var.getAttribute() == null) {
                if (binaryExpression.getRight() instanceof ConstantExpression) {
                    ConstantExpression constantExpression =
                            (ConstantExpression) binaryExpression.getRight();
                    String dvStr = constantExpression.getValue();
                    DataValue dv = null;
                    if (!dvStr.equals("null")) {
                        if (archetypeElementVO == null){
                            throw new InternalErrorException(new Exception("Element '"+var.getCode()+"' not found. (guideId='"+guide.getId()+"')"));
                        }
                        String rmType = archetypeElementVO.getRMType();
                        dv = DataValue.parseValue(rmType + "," + dvStr);
                    }
                    if (dv != null) {
                        sb.append("eval(");
                        if (!OpenEHRConst.CURRENT_DATE_TIME_ID.equals(var.getCode())){
                            sb.append("$" + var.getCode() + ".hasValue() && ");
                        }
                        sb.append(getOperatorMVELLine("$" + var.getCode(),
                                binaryExpression.getOperator(),
                                DVDefSerializer.getDVInstantiation(dv)));
                        sb.append(")");
                    } else {
                        if (OperatorKind.EQUALITY.equals(binaryExpression.getOperator())) {
                            sb.append("eval($" + var.getCode() + ".hasNoValue(\"" + guide.getId() + "/"+var.getCode() + "\"))");
                        } else if (OperatorKind.INEQUAL.equals(binaryExpression.getOperator())) {
                            sb.append("eval($" + var.getCode() + ".hasValue())");
                        }
                    }
                } else if (binaryExpression.getRight() instanceof Variable) {
                    Variable varRight = (Variable) binaryExpression.getRight();
                    String gtCodeAux = varRight.getCode();
                    sb.append("eval($" + var.getCode() + ".hasValue() && ");
                    sb.append("$" + gtCodeAux + ".hasValue() && ");
                    sb.append(getOperatorMVELLine("$"+var.getCode(),
                            binaryExpression.getOperator(), "$" + gtCodeAux));
                    sb.append(")");
                    stats.get(RefStat.REFERENCE).add(gtCodeAux);
                } else {
                    throw new InternalErrorException(new Exception(
                            "Unknown expression '"
                                    + binaryExpression.getRight().getClass()
                                    .getName() + "'"));
                }
            } else {
                if (var.getAttribute().equals(OpenEHRConst.NULL_FLAVOR_ATTRIBUTE)){
                    ConstantExpression constantExpression = (ConstantExpression) binaryExpression.getRight();
                    String dvStr = constantExpression.getValue();
                    DataValue dv = DataValue.parseValue(OpenEHRDataValues.DV_CODED_TEXT + "," + dvStr);
                    sb.append("eval(");
                    String opNeg = (binaryExpression.getOperator().equals(OperatorKind.INEQUAL)) ? "!" : "";
                    sb.append(opNeg + "DVUtil.nullValueEquals($" + var.getCode() + ".getNullFlavour(), " + DVDefSerializer.getDVInstantiation(dv) + "))");
                }else{//Expression
                    Map<RefStat, Set<String>> statsAux = initStats();
                    String arithmeticExpStr =
                            ExpressionUtil.getArithmeticExpressionStr(elementMap, binaryExpression.getRight(), statsAux);
                    //Add stats
                    ExpressionUtil.getArithmeticExpressionStr(elementMap, binaryExpression.getLeft(), statsAux);
                    stats.get(RefStat.REFERENCE).addAll(statsAux.get(RefStat.REFERENCE));
                    stats.get(RefStat.ATT_FUNCTIONS).addAll(statsAux.get(RefStat.ATT_FUNCTIONS));

                    statsAux.get(RefStat.REFERENCE).remove(OpenEHRConst.CURRENT_DATE_TIME_ID);
                    sb.append("eval(");
                    for (String gtCode : statsAux.get(RefStat.REFERENCE)) {
                        sb.append("$" + gtCode + ".hasValue() && ");
                    }
                    String rmName = null;
                    if (archetypeElementVO != null) {
                        rmName = archetypeElementVO.getRMType();
                    }
                    sb.append("(");
                    String varCall = ExpressionUtil.getVariableWithAttributeStr(rmName, var);
                    if (rmName != null && isString(rmName, var.getAttribute())){
                        sb.append(getAttributeOperatorMVELLine(varCall, binaryExpression.getOperator(), arithmeticExpStr));
                    }else{
                        sb.append(varCall);
                        sb.append(binaryExpression.getOperator().getSymbol());
                        sb.append(arithmeticExpStr);
                    }
                    sb.append("))");
                }
            }
        } else {
            throw new InternalErrorException(new Exception("Unknown expression '" + binaryExpression.getLeft() + "'"));
        }
    }

    private String getHasValueStr(Collection<String> gtCodes) {
        if (!gtCodes.isEmpty()) {
            StringBuffer sb = new StringBuffer();
            sb.append("   eval(");
            int count = 0;
            for (String gtCode : gtCodes) {
                sb.append("$" + gtCode + ".hasValue()");
                if (++count < gtCodes.size()) {
                    sb.append(" && ");
                }
            }
            sb.append(")\n");
            return sb.toString();
        } else {
            return null;
        }
    }


    private String getFunctionsExtraCode(Collection<String> gtCodesWithFunctions){
        StringBuffer sb = new StringBuffer();
        for (String gtCodesWithFunction : gtCodesWithFunctions) {
            String[] codeSplit = gtCodesWithFunction.split(ExpressionUtil.CODE_FUNCTION_SEPARATOR);
            String code = codeSplit[0];
            String att = codeSplit[1];
            if (ExpressionUtil.isFunction(att)){
                if (OpenEHRDataValues.FUNCTION_COUNT.equals(att)){
                    //TODO HACK - Should be done in a proper way...
                    String definition = _gtElementToWholeDefinition.get(code);
                    definition = getDefinitionsWithAnds(definition);
                    String defAux = definition
                            .replace("$", "$count_")
                            .replace("eval(DVUtil.equalDV(true, $count_predicate", "eval(DVUtil.equalDV(false, $count_predicate")
                            .replace("eval(DVUtil.isSubClassOf(true, $count_predicate", "eval(DVUtil.isSubClassOf(false, $count_predicate")
                            .replace("$count_" + code + ":ElementInstance(", "$count_" + code + ":ElementInstance(!predicate, dataValue!=null, ")
                            .replace("$count_" + OpenEHRConst.CURRENT_DATE_TIME_ID,"$" + OpenEHRConst.CURRENT_DATE_TIME_ID);
                    /*if (defAux.length()>5){
                        //Remove last ' and\n'+TAB
                        defAux = defAux.substring(0, defAux.length() - 5 - TAB.length());
                    }*/
                    sb.append(TAB);
                    sb.append("Number($"+code+att+":intValue) from accumulate (\n" + TAB + defAux + ",\n" + TAB + TAB + "count($count_" + code + "))\n");
                }
            }
        }
        String str = sb.toString();
        return str.isEmpty()?null:str;
    }

    private String getDefinitionsWithAnds(String definition) {
        String[] definitionLines = definition.split("\n");
        String prefix = "";
        StringBuffer definitionAuxSB = new StringBuffer();
        for (String definitionLine : definitionLines) {
            definitionAuxSB.append(prefix);
            definitionAuxSB.append(definitionLine);
            prefix = " and\n" + TAB;
        }
        definition = definitionAuxSB.toString();
        return definition;
    }

    private String getOperatorMVELLine(String handle, OperatorKind ok, String value) {
        return getOperatorMVELLine(handle, ok, value, false);
    }

    private String getOperatorMVELLine(
            String handle, OperatorKind ok,
            String value, boolean inPredicate) {
        if (OperatorKind.EQUALITY.equals(ok)) {
            return getEqualsString(handle, value, inPredicate, false);
        } else if (OperatorKind.INEQUAL.equals(ok)) {
            return getEqualsString(handle, value, inPredicate, true);
        } else if (OperatorKind.IS_A.equals(ok)) {
            return "DVUtil.isSubClassOf("+ inPredicate+", "+ handle + ", $bindingMap, "+ getTermBindings(value) + ")";
        } else if (OperatorKind.IS_NOT_A.equals(ok)) {
            return "DVUtil.isNotSubClassOf(" + inPredicate + ", "+ handle+", $bindingMap, "+ getTermBindings(value) + ")";
        } else if (OperatorKind.GREATER_THAN.equals(ok)) {
            return getComparisonString(handle, value) + ">0";
        } else if (OperatorKind.GREATER_THAN_OR_EQUAL.equals(ok)) {
            return getComparisonString(handle, value) + ">=0";
        } else if (OperatorKind.LESS_THAN.equals(ok)) {
            return getComparisonString(handle, value) + "<0";
        } else if (OperatorKind.LESS_THAN_OR_EQUAL.equals(ok)) {
            return getComparisonString(handle, value) + "<=0";
        } else {
            return null;
        }
    }


    private static String getEqualsString(String handle, String value, boolean inPredicate, boolean negated){
        StringBuffer sb = new StringBuffer();
        sb.append("DVUtil.equalDV("+inPredicate+", "+handle+"," + value);
        sb.append(getDataValueStrIfNeeded(value)+", "+negated+")");
        return sb.toString();
    }

    private static String getComparisonString(String handle, String value){
        StringBuffer sb = new StringBuffer();
        sb.append("DVUtil.compatibleComparison(" + handle +getDataValueStrIfNeeded(handle)+ ", $auxDV="+ value+getDataValueStrIfNeeded(value) + ") && ");
        sb.append("DVUtil.compareDVs("+handle+".getDataValue(), $auxDV)");
        return sb.toString();
    }

    private static String getDataValueStrIfNeeded(String value){
        if (value.startsWith("$")){
            return ".getDataValue()";
        }else{
            return "";
        }
    }


    private String getAttributeOperatorMVELLine(
            String handle,
            OperatorKind ok,
            String value) {
        if (OperatorKind.EQUALITY.equals(ok)) {
            return handle + ".equals("+ value + ")";
        } else if (OperatorKind.INEQUAL.equals(ok)) {
            return "!" + handle + ".equals(" + value + ")";
        } else {
            Logger.getLogger(GDLDroolsConverter.class).warn("Guide="+guide.getId()+", Illegal operator '"+ok.getSymbol()+"' used in handle '"+handle+"'.");
            return "false";
        }
    }

    /*
     * Parse code from string value and generate right 
     * code_phrase array for subClass evaluation
     * 
     * possible values:
     * 1. new DvCodedText("Dubois and Dubois","local","gt0008")
     * 2. new DvText("local::gt0100")
     * 2. new DvText("local::gt0100|Hypertension|")
     */
    protected String parseCode(String value) {
        int i = value.indexOf("local");

        log.debug("value after IS_A: " + value);

        if (i < 0) {
            return value;
        }

        String code;


        if(value.contains("DvCodedText")) {
            code = value.substring(i + 8, value.length() - 2);
        } else if(value.contains("'")) { // due to a logic somewhere in gdl-editor introducing single quotation to code_phrase
            code = value.substring(i + 7, value.length() - 3);
        } else {
            code = value.substring(i + 7, value.length() - 2);
        }

        int j = code.indexOf("|");
        if(j > 0) {
            code = code.substring(0, j);
        }

        log.debug("code parsed from value: " + code);

        return code;
    }

    private String getTermBindings(String value) {
        if (value.startsWith("$")){
            Logger.getLogger(GDLDroolsConverter.class).warn("Guide="+guide.getId()+", Subclass comparison between elements is not supported.");
            //TODO Give support to subclass comparison between elements
            return "null";
        }
        Map<String, TermBinding> termBindings = guide.getOntology().getTermBindings();
        // TODO log.warn if gt code is unbound to terminologies
        if(termBindings == null) {
            //Logger.getLogger(GDLDroolsConverter.class).warn("Guide="+guide.getId()+", Needed terminology binding not found on guide.");
            return value;
        }
        String code = parseCode(value);
        StringBuffer buf = new StringBuffer("new DvCodedText[] {");
        boolean first = true;
        for(String terminology : termBindings.keySet()) {
            log.debug("terminology: " + terminology);
            TermBinding termBinding = termBindings.get(terminology);
            Map<String, Binding> bindings = termBinding.getBindings();
            log.debug("bindings: " + bindings);
            if(bindings.containsKey(code)) {
                log.debug("hasCode: " + code);
                Binding binding = bindings.get(code);
                if(binding.getCodes() != null) {
                    for(CodePhrase cp : binding.getCodes()) {
                        if(first) {
                            first = false;
                        } else {
                            buf.append(",");
                        }
                        buf.append("new DvCodedText(\"text\",\"");
                        buf.append(terminology);
                        buf.append("\",\"");
                        buf.append(cp.getCodeString());
                        buf.append("\")");
                    }
                }
            }
        }
        buf.append("}");
        return buf.toString();
    }

    private boolean isString(String rmName, String attribute){
        return (OpenEHRDataValues.DV_TEXT.equals(rmName) && OpenEHRDataValues.VALUE_ATT.equals(attribute)) ||
                (OpenEHRDataValues.DV_CODED_TEXT.equals(rmName) && OpenEHRDataValues.VALUE_ATT.equals(attribute)) ||
                OpenEHRDataValues.UNITS_ATT.equals(attribute) ||
                OpenEHRDataValues.CODE_ATT.equals(attribute) ||
                OpenEHRDataValues.TEMINOLOGYID_ATT.equals(attribute);
    }

    private String getGuideHeader() {
        return "package se.cambio.cds;\n"
                + "import se.cambio.cds.model.instance.ArchetypeReference;\n"
                + "import se.cambio.cds.model.instance.ElementInstance;\n"
                + "import se.cambio.cds.model.instance.ContainerInstance;\n"
                + "import se.cambio.cds.model.instance.FiredRuleReference;\n"
                + "import se.cambio.cds.util.DVUtil;\n"
                + "import org.openehr.rm.datatypes.quantity.DvOrdered;\n"
                + "import org.openehr.rm.datatypes.quantity.DvCount;\n"
                + "import org.openehr.rm.datatypes.quantity.DvOrdinal;\n"
                + "import org.openehr.rm.datatypes.quantity.DvQuantity;\n"
                + "import org.openehr.rm.datatypes.quantity.datetime.DvDate;\n"
                + "import org.openehr.rm.datatypes.quantity.datetime.DvDateTime;\n"
                + "import org.openehr.rm.datatypes.quantity.datetime.DvDuration;\n"
                + "import org.openehr.rm.datatypes.quantity.datetime.DvTime;\n"
                + "import org.openehr.rm.datatypes.quantity.DvProportion;\n"
                + "import org.openehr.rm.datatypes.quantity.ProportionKind;\n"
                + "import org.openehr.rm.datatypes.basic.DvBoolean;\n"
                + "import org.openehr.rm.datatypes.text.DvCodedText;\n"
                + "import org.openehr.rm.datatypes.text.DvText;\n"
                + "global se.cambio.cds.util.ExecutionLogger $executionLogger;\n"
                + "global org.openehr.rm.datatypes.basic.DataValue $auxDV;\n"
                + "global org.openehr.rm.datatypes.quantity.datetime.DvDateTime $" + OpenEHRConst.CURRENT_DATE_TIME_ID + ";\n"
                + "global java.util.Map<se.cambio.cds.model.instance.ElementInstance, java.util.Map<String, Boolean>> $bindingMap;\n"
                + "global java.lang.Integer " + DroolsExecutionManager.getGuideSalienceId(guide.getId()) + ";\n"
                + "\n";
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