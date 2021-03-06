/*
 *
 * Copyright (c) 2013 - 2016 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ca.dbtool.port;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.datasource.DataSourceFactory;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.password.PasswordResolver;
import org.xipki.commons.password.PasswordResolverException;
import org.xipki.pki.ca.dbtool.jaxb.ca.ObjectFactory;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaDbImportWorker extends DbPortWorker {

    private static final Logger LOG = LoggerFactory.getLogger(CaDbImportWorker.class);

    private final DataSourceWrapper datasource;

    private final Unmarshaller unmarshaller;

    private final boolean resume;

    private final String srcFolder;

    private final int batchEntriesPerCommit;

    private final boolean evaluateOnly;

    public CaDbImportWorker(final DataSourceFactory datasourceFactory,
            final PasswordResolver passwordResolver, final String dbConfFile, final boolean resume,
            final String srcFolder, final int batchEntriesPerCommit, final boolean evaluateOnly)
    throws DataAccessException, PasswordResolverException, IOException, JAXBException {
        ParamUtil.requireNonNull("datasourceFactory", datasourceFactory);

        Properties props = DbPorter.getDbConfProperties(
                new FileInputStream(IoUtil.expandFilepath(dbConfFile)));
        this.datasource = datasourceFactory.createDataSource("ds-" + dbConfFile, props,
                passwordResolver);
        JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(DbPorter.retrieveSchema("/xsd/dbi-ca.xsd"));
        this.resume = resume;
        this.srcFolder = IoUtil.expandFilepath(srcFolder);
        this.batchEntriesPerCommit = batchEntriesPerCommit;
        this.evaluateOnly = evaluateOnly;
    }

    @Override
    public void doRun() throws Exception {
        File processLogFile = new File(srcFolder, DbPorter.IMPORT_PROCESS_LOG_FILENAME);
        if (resume) {
            if (!processLogFile.exists()) {
                throw new Exception("could not process with '--resume' option");
            }
        } else {
            if (processLogFile.exists()) {
                throw new Exception("please either specify '--resume' option or delete the file "
                        + processLogFile.getPath() + " first");
            }
        }

        long start = System.currentTimeMillis();
        try {
            if (!resume) {
                // CAConfiguration
                CaConfigurationDbImporter caConfImporter = new CaConfigurationDbImporter(datasource,
                        unmarshaller, srcFolder, stopMe, evaluateOnly);
                caConfImporter.importToDb();
                caConfImporter.shutdown();
            }

            // CertStore
            CaCertStoreDbImporter certStoreImporter = new CaCertStoreDbImporter(datasource,
                    unmarshaller, srcFolder, batchEntriesPerCommit, resume, stopMe, evaluateOnly);
            certStoreImporter.importToDb();
            certStoreImporter.shutdown();
        } finally {
            try {
                datasource.shutdown();
            } catch (Throwable th) {
                LOG.error("datasource.shutdown()", th);
            }
            long end = System.currentTimeMillis();
            System.out.println("Finished in " + StringUtil.formatTime((end - start) / 1000, false));
        }
    } // method doRun

}
