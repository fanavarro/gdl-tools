package se.cambio.cds.gdl.editor.controller.interfaces;

import se.cambio.cds.gdl.model.ResourceDescription;
import se.cambio.cds.gdl.model.Term;
import se.cambio.openehr.util.exceptions.InternalErrorException;

import javax.swing.*;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

/**
 * User: Iago.Corbal
 * Date: 2014-09-05
 * Time: 08:26
 */
public interface EditorController<E> {

    public void init();
    public E getEntity();
    public void setEntity(E entity);
    public String getTitle();
    public JPanel getEditorPanel();
    public void runIfOKToExit(Runnable runnable);
    public String createNextGTCode();
    public Map<String, Term> getCurrentTermsMap();
    public Collection<String> getUsedCodes();
    public ResourceDescription getResourceDescription();
    public Term getConceptTerm();
    public String getCurrentLanguageCode();
    public Collection<String> getSupportedLanguageCodes();
    public String getEntityId();
    public String getEntityName();
    public Collection<String> getSupportedEntityExtensions();
    public void setEntityId(String entityId);
    public void save();
    public void saveAs();
    public void entitySaved();
    public String getSerializedEntity();
    public E parseEntity(InputStream is);
}