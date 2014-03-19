import junit.framework.TestCase;
import se.cambio.openehr.controller.session.data.*;
import se.cambio.openehr.util.UserConfigurationManager;
import se.cambio.openehr.util.exceptions.InternalErrorException;

/**
 * User: Iago.Corbal
 * Date: 2014-03-17
 * Time: 11:02
 */
public class ArchetypeElementsTest extends TestCase {


    public void testArchetypeElementsLanguages(){
        try {
            UserConfigurationManager.setParameter(UserConfigurationManager.ARCHETYPES_FOLDER_KW, ArchetypeElementsTest.class.getClassLoader().getResource("archetypes").getPath());
            Archetypes.loadArchetypes();

            UserConfigurationManager.setParameter(UserConfigurationManager.TEMPLATES_FOLDER_KW, ArchetypeElementsTest.class.getClassLoader().getResource("templates").getPath());
            Templates.loadTemplates();

            String text = ArchetypeElements.getText(null,"openEHR-EHR-OBSERVATION.chadsvas_score.v1/data[at0002]/events[at0003]/data[at0001]/items[at0026]","sv");
            assertTrue(text.equals("Hjärtsvikt/VK-dysfunktion"));

            text = Ordinals.getText(null, "openEHR-EHR-OBSERVATION.chadsvas_score.v1/data[at0002]/events[at0003]/data[at0001]/items[at0026]", 0, "sv");
            assertTrue(text.equals("Finns ej"));

            text = CodedTexts.getText(null, "openEHR-EHR-OBSERVATION.basic_demographic.v1/data[at0001]/events[at0002]/data[at0003]/items[at0004]", "at0006", "sv");
            assertTrue(text.equals("Kvinna"));

            text = Clusters.getText("medication_atc_indicator", "openEHR-EHR-INSTRUCTION.medication.v1/activities[at0001]/description[openEHR-EHR-ITEM_TREE.medication.v1]/items[at0033]", "sv");
            assertTrue(text.equals("Dose")); //No translation to swedish
        } catch (InternalErrorException e) {
            e.printStackTrace();
        }
    }
}
