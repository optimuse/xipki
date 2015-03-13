/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2015 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

package org.xipki.ca.client.shell;

import java.security.cert.X509Certificate;
import java.util.Date;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.bouncycastle.asn1.x500.X500Name;
import org.xipki.ca.client.api.CertIDOrError;
import org.xipki.ca.common.cmp.PKIStatusInfo;
import org.xipki.common.CRLReason;
import org.xipki.common.RequestResponseDebug;
import org.xipki.common.qa.UnexpectedResultException;
import org.xipki.common.util.DateUtil;
import org.xipki.common.util.SecurityUtil;

/**
 * @author Lijun Liao
 */

@Command(scope = "xipki-cli", name = "revoke", description="revoke certificate")
public class RevokeCertCommand extends UnRevRemoveCertCommand
{
    @Option(name = "-reason",
            required = true,
            description = "CRL reason\n"
                    + "required")
    private String reason;

    @Option(name = "-invDate",
            description = "invalidity date, UTC time of format yyyyMMddHHmmss")
    private String invalidityDateS;

    @Override
    protected Object _doExecute()
    throws Exception
    {
        if(certFile == null && (caCertFile == null || getSerialNumber() == null))
        {
            err("either cert or (cacert, serial) must be specified");
            return null;
        }

        CRLReason crlReason = CRLReason.getInstance(reason);
        if(crlReason == null)
        {
            err("invalid reason " + reason);
            return null;
        }

        if(CRLReason.PERMITTED_CLIENT_CRLREASONS.contains(crlReason) == false)
        {
            err("reason " + reason + " is not permitted");
            return null;
        }

        CertIDOrError certIdOrError;
        X509Certificate caCert = null;
        if(caCertFile != null)
        {
            caCert = SecurityUtil.parseCert(caCertFile);
        }

        Date invalidityDate = null;
        if(isNotBlank(invalidityDateS))
        {
            invalidityDate = DateUtil.parseUTCTimeyyyyMMddhhmmss(invalidityDateS);
        }

        if(certFile != null)
        {
            X509Certificate cert = SecurityUtil.parseCert(certFile);
            if(caCert != null)
            {
                String errorMsg = checkCertificate(cert, caCert);
                if(errorMsg != null)
                {
                    err(errorMsg);
                    return null;
                }
            }
            RequestResponseDebug debug = getRequestResponseDebug();
            try
            {
                certIdOrError = raWorker.revokeCert(cert, crlReason.getCode(), invalidityDate, debug);
            }finally
            {
                saveRequestResponse(debug);
            }
        }
        else
        {
            X500Name issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
            RequestResponseDebug debug = getRequestResponseDebug();
            try
            {
                certIdOrError = raWorker.revokeCert(issuer, getSerialNumber(), crlReason.getCode(),
                        invalidityDate, debug);
            }finally
            {
                saveRequestResponse(debug);
            }
        }

        if(certIdOrError.getError() != null)
        {
            PKIStatusInfo error = certIdOrError.getError();
            throw new UnexpectedResultException("revocation failed: " + error);
        }
        else
        {
            out("revoked certificate");
        }
        return null;
    }

}
