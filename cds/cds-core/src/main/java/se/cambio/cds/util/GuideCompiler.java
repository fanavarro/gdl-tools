package se.cambio.cds.util;

import se.cambio.cds.gdl.model.Guide;
import se.cambio.openehr.util.exceptions.InternalErrorException;

public interface GuideCompiler {
    byte[] compile(Guide guide) throws InternalErrorException;
}
