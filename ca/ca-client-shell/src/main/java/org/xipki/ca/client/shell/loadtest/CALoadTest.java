/*
 * Copyright 2014 xipki.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.xipki.ca.client.shell.loadtest;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.Locale;

import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.crmf.CertTemplateBuilder;
import org.bouncycastle.asn1.crmf.ProofOfPossession;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.client.api.RAWorker;
import org.xipki.ca.cmp.client.type.EnrollCertRequestEntryType;
import org.xipki.ca.cmp.client.type.EnrollCertRequestType;
import org.xipki.ca.cmp.client.type.EnrollCertRequestType.Type;
import org.xipki.ca.common.CertificateOrError;
import org.xipki.ca.common.EnrollCertResult;
import org.xipki.ca.common.PKIErrorException;
import org.xipki.ca.common.RAWorkerException;
import org.xipki.security.SignerUtil;
import org.xipki.security.common.ParamChecker;

public class CALoadTest extends AbstractLoadTest 
{
	private static final Logger LOG = LoggerFactory.getLogger(CALoadTest.class);
	
	private final RAWorker raWorker;
	private final String certProfile;
	private final String commonNamePrefix;
	private final String otherPartOfSubject;
	
	private long index;
	private final BigInteger baseN;
	
	@Override
	protected Runnable getTestor() throws Exception {
		return new Testor();
	}
    
	public CALoadTest(RAWorker raWorker, String certProfile,
			String commonNamePrefix, 
			String otherPartOfSubject)
	{
		ParamChecker.assertNotNull("raWorker", raWorker);
		ParamChecker.assertNotEmpty("certProfile", certProfile);
		ParamChecker.assertNotEmpty("commonNamePrefix", commonNamePrefix);
		ParamChecker.assertNotEmpty("otherPartOfSubject", otherPartOfSubject);
		
		this.raWorker = raWorker;
		this.certProfile = certProfile;
		this.commonNamePrefix = commonNamePrefix;
		this.otherPartOfSubject = otherPartOfSubject;
		
		this.baseN = BigInteger.valueOf(0);
		this.baseN.setBit(2047);
		for(int i = 32; i < 2047; i+= 2)
		{
			this.baseN.setBit(i);
		}
		
		Calendar baseTime = Calendar.getInstance(Locale.UK);
		baseTime.set(Calendar.YEAR, 2014);
		baseTime.set(Calendar.MONTH, 0);
		baseTime.set(Calendar.DAY_OF_MONTH, 1);
		
		this.index = System.nanoTime() / 10000L - baseTime.getTimeInMillis() * 10L;
	}
	
	private synchronized long nextIndex()
	{
		return index++;
	}
	
	private CertRequest nextCertRequest()
	{
		CertTemplateBuilder certTempBuilder = new CertTemplateBuilder();
		
		long thisIndex = nextIndex();
		
		X500Name subject = new X500Name("CN=" + commonNamePrefix + thisIndex + "," + otherPartOfSubject);
		certTempBuilder.setSubject(subject);
				
		BigInteger modulus = baseN.add(BigInteger.valueOf(thisIndex));
		
		SubjectPublicKeyInfo spki;
		try {
			spki = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
					SignerUtil.generateRSAPublicKeyParameter(
							org.xipki.security.KeyUtil.generateRSAPublicKey(modulus,
									BigInteger.valueOf(65535))));
		} catch (InvalidKeySpecException e) {
			LOG.warn("InvalidKeySpecException: {}", e.getMessage());
			return null;
		} catch (IOException e) {
			LOG.warn("IOException: {}", e.getMessage());
			return null;
		}
		certTempBuilder.setPublicKey(spki);

		CertTemplate certTemplate = certTempBuilder.build();
		return new CertRequest(1, certTemplate, null);
	}
	
	class Testor implements Runnable
	{		
		
		@Override
		public void run() {
			while(stop() == false && getErrorAccout() < 10)
			{
				CertRequest certReq = nextCertRequest();
				if(certReq != null)
				{
					account(1, (testNext(certReq)? 0: 1));
				}
				else
				{
					account(1, 1);
				}
			}
		}
		
		private boolean testNext(CertRequest certRequest)
		{			
			EnrollCertResult result;
			try {
				EnrollCertRequestEntryType requestEntry = new EnrollCertRequestEntryType
						("id-1", certProfile, certRequest, new ProofOfPossession());
				
				EnrollCertRequestType request = new EnrollCertRequestType(Type.CERT_REQ);
				request.addRequestEntry(requestEntry);
				
				result = raWorker.requestCerts(request, null);
			} catch (RAWorkerException e) {
				LOG.warn("RAWorkerException: {}", e.getMessage());
				return false;
			} catch (PKIErrorException e) {
				LOG.warn("PKIErrorException: {}", e.getMessage());
				return false;
			}		
			
			X509Certificate cert = null;
			if(result != null)
			{
				String id = result.getAllIds().iterator().next();
				CertificateOrError certOrError = result.getCertificateOrError(id);
				cert = (X509Certificate) certOrError.getCertificate();
			}
			
			if(cert == null)
			{
				return false;
			}
			
			return true;
		}
		
	} // End class OcspRequestor	

}