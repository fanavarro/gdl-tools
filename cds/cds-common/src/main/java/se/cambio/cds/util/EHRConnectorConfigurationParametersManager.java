package se.cambio.cds.util;

import org.apache.log4j.Logger;
import se.cambio.openehr.util.exceptions.MissingConfigurationParameterException;
import se.cambio.openehr.util.misc.CDSConfigurationParametersManager;

import javax.naming.InitialContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

public final class EHRConnectorConfigurationParametersManager {

    public static final String EHR_USERNAME = "EhrUsername";
    public static final String EHR_PASSWORD = "EhrPassword";
    public static final String EHR_HOST = "EhrHost";
    public static final String EHR_PORT = "EhrPort";
    public static final String EHR_NAMESPACE = "EhrSubjectNamespace";
    public static final String REMOTE_LOGGER_URL = "RemoteLoggerURL";
    private static final String JNDI_PREFIX = "java:global/cds/";
    private static final String CONFIGURATION_FOLDER = "conf";
    private static final String CDS_CONFIG_DIR = "CDS_CONFIG_DIR";
    private static final Map<String, String> defaultParameters =
            new HashMap<String, String>() {
                {
                    put(EHR_USERNAME, "admin");
                    put(EHR_PASSWORD, "admin");
                    put(EHR_HOST, "localhost");
                    put(EHR_PORT, "7778");
                    put(EHR_NAMESPACE, "default");
                }
            };
    static {
        /*
         * We use a synchronized map because it will be filled by using a lazy strategy.
         */
        parameters = Collections.synchronizedMap(new HashMap<Object, Object>());
        try {
            /* Read property file (if exists).*/
            Class<EHRConnectorConfigurationParametersManager> configurationParametersManagerClass =
                    EHRConnectorConfigurationParametersManager.class;
            ClassLoader classLoader =
                    configurationParametersManagerClass.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream(EHR_CONNECTOR_FILE);
            if (inputStream != null) {
                Logger.getLogger(EHRConnectorConfigurationParametersManager.class).info("*** Using resource for '" + EHR_CONNECTOR_FILE + "'");
            } else {
                File configFile = getConfigFile();
                if (configFile != null) {
                    inputStream = new FileInputStream(configFile);
                    Logger.getLogger(EHRConnectorConfigurationParametersManager.class).info("*** Using '" + CONFIGURATION_FOLDER + "' folder for '" + EHR_CONNECTOR_FILE + "'");
                } else {
                    inputStream = classLoader.getResourceAsStream(DEFAULT_EHR_CONNECTOR_FILE);
                    Logger.getLogger(CDSConfigurationParametersManager.class).info("*** Using resource for '" + DEFAULT_EHR_CONNECTOR_FILE + "'");
                }
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            inputStream.close();

	        /* We have been able to read the file. */
            usesJNDI = false;
            parameters.putAll(properties);
        } catch (Exception e) {
            /* We have not been able to read the file. */
            usesJNDI = true;
            Logger.getLogger(EHRConnectorConfigurationParametersManager.class).info("*** Using JNDI for '" + EHR_CONNECTOR_FILE + "'");
        }
    }
    private static String EHR_CONNECTOR_FILE = "cds-ehr-connector.properties";
    private static String DEFAULT_EHR_CONNECTOR_FILE = "default-cds-ehr-connector.properties";
    private static String OPT_EHR_CONNECTOR_FILE = "/opt/cds-config/" + EHR_CONNECTOR_FILE;
    private static boolean usesJNDI;
    private static Map<Object, Object> parameters;

    private EHRConnectorConfigurationParametersManager() {
    }

    private static File getConfigFile() {
        try {
            File jarFile = new File(EHRConnectorConfigurationParametersManager.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            //../conf
            File parentFile1 = jarFile.getParentFile();
            if (parentFile1 != null) {
                File parentFile = parentFile1.getParentFile();
                if (parentFile != null) {
                    for (File file : parentFile.listFiles()) {
                        if (file.isDirectory() && file.getName().equals(CONFIGURATION_FOLDER)) {
                            for (File file2 : file.listFiles()) {
                                if (file2.getName().equals(EHR_CONNECTOR_FILE)) {
                                    return file2;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            //Problem finding config folder
            //Loggr.getLogger(UserConfigurationManager.class).warn("CONF Folder not found "+t.getMessage());
        }
        try {
            //Current folder
            File file = new File(CONFIGURATION_FOLDER + File.separator + EHR_CONNECTOR_FILE);
            if (file.exists()) {
                return file;
            }
        } catch (Throwable t2) {
            //Problem finding config folder
            //Logger.getLogger(UserConfigurationManager.class).warn("CONF Folder not found "+t.getMessage());
        }

        try {
            //CDS_CONFIG_DIR param
            String configDir = System.getenv(CDS_CONFIG_DIR);
            if (configDir != null) {
                File file = new File(configDir + "/" + EHR_CONNECTOR_FILE);
                if (file.exists()) {
                    return file;
                }
            }
        } catch (Throwable t2) {
            //Problem finding config folder
            //Logger.getLogger(UserConfigurationManager.class).warn("CONF Folder not found "+t.getMessage());
        }

        try {
            //OPT folder
            File file = new File(OPT_EHR_CONNECTOR_FILE);
            if (file.exists()) {
                return file;
            }
        } catch (Throwable t2) {
            //Problem finding config folder
            //Logger.getLogger(UserConfigurationManager.class).warn("CONF Folder not found "+t.getMessage());
        }
        return null;
    }

    public static String getParameter(String key)
            throws MissingConfigurationParameterException {
        String value = (String) parameters.get(key);
        if (value == null) {
            if (usesJNDI) {
                try {
                    InitialContext initialContext = new InitialContext();
                    value = (String) initialContext.lookup(JNDI_PREFIX + key);
                    parameters.put(key, value);
                } catch (Exception e) {
                    value = defaultParameters.get(key);
                    if (value != null) {
                        Logger.getLogger(EHRConnectorConfigurationParametersManager.class).warn("Using default value for '" + key + "' = '" + value + "'");
                        return value;
                    } else {
                        throw new MissingConfigurationParameterException(key);
                    }
                }
            } else {
                throw new MissingConfigurationParameterException(key);

            }
        }
        return value;
    }

    public static Map<Object, Object> getParameters() {
        return parameters;
    }

    public static void loadParameters(Hashtable<Object, Object> usrConfig) {
        parameters.putAll(usrConfig);
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