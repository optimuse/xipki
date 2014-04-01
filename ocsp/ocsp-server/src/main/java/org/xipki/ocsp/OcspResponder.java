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

package org.xipki.ocsp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.ocsp.CertHash;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.database.api.DataSource;
import org.xipki.database.api.DataSourceFactory;
import org.xipki.ocsp.api.CertRevocationInfo;
import org.xipki.ocsp.api.CertStatus;
import org.xipki.ocsp.api.CertStatusInfo;
import org.xipki.ocsp.api.CertStatusStore;
import org.xipki.ocsp.api.CertStatusStoreException;
import org.xipki.ocsp.api.HashAlgoType;
import org.xipki.ocsp.crlstore.CrlCertStatusStore;
import org.xipki.ocsp.dbstore.DbCertStatusStore;
import org.xipki.security.api.ConcurrentContentSigner;
import org.xipki.security.api.PasswordResolver;
import org.xipki.security.api.PasswordResolverException;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.api.SignerException;
import org.xipki.security.common.IoCertUtil;

public class OcspResponder {
	public static final String signer_type = "signer.type";
	public static final String signer_conf = "signer.conf";
	public static final String signer_cert = "signer.cert";	
	public static final String dbstore_prefix = "dbstore.";	
	public static final String crlstore_prefix = "crlstore.";	
	public static final String enabled_suffix = ".enabled";
	public static final String conffile_suffix = ".confFile";
	public static final String useUpdateDatesFromCRL_suffix = ".useUpdateDatesFromCRL";
	public static final String crlFile_SUFFIX = ".crlFile";
	public static final String cacertFile_SUFFIX = ".cacertFile";
	public static final String issuerCertFile_SUFFIX = ".issuerCertFile";
	public static final String unknownSerialAsGood_SUFFIX = ".unknownSerialAsGood";
	
    private static final Logger LOG = LoggerFactory.getLogger(OcspResponder.class);
	private boolean includeCertHash = false;
	private boolean requireReqSigned = false;
	private boolean checkReqSignature = false;

	private ResponderSigner responder;

	private List<CertStatusStore> certStatusStores = new ArrayList<CertStatusStore>();
	
	private DataSourceFactory dataSourceFactory;
	private SecurityFactory securityFactory;
	private PasswordResolver passwordResolver;
	
	private String confFile;	

	public OcspResponder()
	{		
	}

	public void init()
		throws OCSPResponderException
	{
		if(confFile == null)
		{
			throw new IllegalStateException("confFile is not set");
		}
		if(dataSourceFactory == null)
		{
			throw new IllegalStateException("dataSourceFactory is not set");
		}
		if(securityFactory == null)
		{
			throw new IllegalStateException("securityFactory is not set");
		}
		if(passwordResolver == null)
		{
			throw new IllegalStateException("passwordResolver is not set");
		}
		
        if(Security.getProvider("BC") == null)
        {
            Security.addProvider(new BouncyCastleProvider());
        }
 
		Properties props = new Properties();
		FileInputStream configStream = null;
		try {
			configStream = new FileInputStream(confFile);
			props.load(configStream);
		} catch (FileNotFoundException e) {
			throw new OCSPResponderException(e);
		} catch (IOException e) {
			throw new OCSPResponderException(e);
		}finally{
			if(configStream != null)
			{
				try{
					configStream.close();
				}catch(IOException e)
				{}
			}
		}
		
		X509Certificate requestorCert = null;
		String s = props.getProperty(signer_cert);
		if(s != null && s.isEmpty() == false)
		{
			requestorCert = parseCert(s);
		}
		
		String requestorSignerType = props.getProperty(signer_type);
		String requestorSignerConf = props.getProperty(signer_conf);
		
		ConcurrentContentSigner requestorSigner;
		try {
			requestorSigner = securityFactory.createSigner(
					requestorSignerType, requestorSignerConf, requestorCert, passwordResolver);
		} catch (SignerException e) {
			throw new OCSPResponderException(e);
		} catch (PasswordResolverException e) {
			throw new OCSPResponderException(e);
		}
		
		try {
			responder = new ResponderSigner(requestorSigner);
		} catch (CertificateEncodingException e) {
			throw new OCSPResponderException(e);
		} catch (IOException e) {
			throw new OCSPResponderException(e);
		}
		
		List<String> dbStoreNames = new ArrayList<String>();
		
		for(Object _propKey : props.keySet())
		{
			String propKey = (String) _propKey;
			if(propKey.startsWith(dbstore_prefix) && propKey.endsWith(conffile_suffix))
			{
				String certstoreName = propKey.substring(dbstore_prefix.length(),
						propKey.length() - conffile_suffix.length());
				
				String enabled = props.getProperty(dbstore_prefix + certstoreName + enabled_suffix, "true");
				if(Boolean.parseBoolean(enabled) && dbStoreNames.contains(certstoreName) == false)
				{
					dbStoreNames.add(certstoreName);
				}
				else
				{
					LOG.info("Database-based certificate store " + certstoreName + " is disabled");
				}
			}
		}
		
		List<String> crlStoreNames = new ArrayList<String>();
		
		for(Object _propKey : props.keySet())
		{
			String propKey = (String) _propKey;
			if(propKey.startsWith(crlstore_prefix) && propKey.endsWith(crlFile_SUFFIX))
			{
				String certstoreName = propKey.substring(crlstore_prefix.length(),
						propKey.length() - crlFile_SUFFIX.length());
				
				String enabled = props.getProperty(crlstore_prefix + certstoreName + enabled_suffix, "true");
				if(Boolean.parseBoolean(enabled) && crlStoreNames.contains(certstoreName) == false)
				{
					crlStoreNames.add(certstoreName);
				}
				else
				{
					LOG.info("CRL-based certificate store " + certstoreName + " is disabled");
				}
			}
		}
		
		if(dbStoreNames.isEmpty() && crlStoreNames.isEmpty())
		{
			throw new OCSPResponderException("No Certificate Store is configured");
		}
		
		if(dbStoreNames.isEmpty() == false)
		{
			for(String storeName : dbStoreNames)
			{
				FileInputStream confStream = null;
				
	            String tmp = props.getProperty(dbstore_prefix + storeName + unknownSerialAsGood_SUFFIX);
	            boolean unknownSerialAsGood = (tmp == null) ? false : Boolean.parseBoolean(tmp); 

				String dbConfFile = props.getProperty(dbstore_prefix + storeName + conffile_suffix);
	            DataSource dataSource;
	            try {
	            	confStream = new FileInputStream(dbConfFile);
	                dataSource = dataSourceFactory.createDataSource(confStream, passwordResolver);
	            } catch (IOException e) {
	                    throw new OCSPResponderException(e);
	            } catch (SQLException e) {
	                    throw new OCSPResponderException(e);
	            } catch (PasswordResolverException e) {
	                    throw new OCSPResponderException(e);
	            } finally
	            {
	            	if(confStream != null)
	            	{
	            		try{
	            			confStream.close();
	            		}catch(IOException e){}
	            	}
	            }
	            
	            try {
	            	DbCertStatusStore certStatusStore = new DbCertStatusStore(dataSource, unknownSerialAsGood);
	            	this.certStatusStores.add(certStatusStore);
				} catch (NoSuchAlgorithmException e) {
					throw new OCSPResponderException(e);
				} catch (SQLException e) {
					throw new OCSPResponderException(e);
				}
			}			
		}
		
		if(crlStoreNames.isEmpty() == false)
		{
			for(String storeName : crlStoreNames)
			{
	            String tmp = props.getProperty(crlstore_prefix + storeName + unknownSerialAsGood_SUFFIX);
	            boolean unknownSerialAsGood = (tmp == null) ? false : Boolean.parseBoolean(tmp); 
	            
				String key = crlstore_prefix + storeName + crlFile_SUFFIX;
				String crlFile = props.getProperty(key);
				if(crlFile == null)
				{
					throw new OCSPResponderException(key + " is not set");
				}
				
				key = crlstore_prefix + storeName + cacertFile_SUFFIX;
				String cacertFile = props.getProperty(key);
				if(cacertFile == null)
				{
					throw new OCSPResponderException(key + " is not set");
				}
				
				String issuercertFile = props.getProperty(crlstore_prefix + storeName + issuerCertFile_SUFFIX);
				String s1 = props.getProperty(crlstore_prefix + storeName + useUpdateDatesFromCRL_suffix);
				boolean useUpdateDatesFromCRL = (s1 == null)? true : Boolean.getBoolean(s1); 
				
				X509CRL crl = parseCRL(crlFile);
				
				X509Certificate caCert = parseCert(cacertFile);				
				X509Certificate crlIssuerCert = issuercertFile == null ? null : parseCert(issuercertFile);
				
				CrlCertStatusStore certStatusStore;
				try {
					certStatusStore = new CrlCertStatusStore(crl, caCert, crlIssuerCert,
							useUpdateDatesFromCRL, unknownSerialAsGood);
				} catch (CertStatusStoreException e) {
					throw new OCSPResponderException(e);
				}
				this.certStatusStores.add(certStatusStore);
			}
		}		

	}
	
	public OCSPResp answer(OCSPReq request)
	{
		try{			
			if(request.isSigned())
			{
				if(checkReqSignature)
				{
					X509CertificateHolder[] certs = request.getCerts();
					if(certs == null || certs.length < 1)
					{
						LOG.warn("no certificate found in request to verify the signature");
						return createUnsuccessfullOCSPResp(CSPResponseStatus.malformedRequest);
					}
					
					ContentVerifierProvider cvp;
					try{
						cvp = securityFactory.getContentVerifierProvider(certs[0]);
					}catch(InvalidKeyException e)
					{
						LOG.warn("securityFactory.getContentVerifierProvider, InvalidKeyException: {}", e.getMessage());
						return createUnsuccessfullOCSPResp(CSPResponseStatus.malformedRequest);
					}
					
					boolean sigValid = request.isSignatureValid(cvp);
					if(!sigValid)
					{
						LOG.warn("request signature is invalid");
						return createUnsuccessfullOCSPResp(CSPResponseStatus.malformedRequest);
					}
				}
			}
			else
			{
				if(requireReqSigned)
				{
					return createUnsuccessfullOCSPResp(CSPResponseStatus.sigRequired);
				}
			}
			
			Req[] requestList = request.getRequestList();
			int n = requestList.length;
	
			RespID respID = new RespID(responder.getResponderId());		
			BasicOCSPRespBuilder basicOcspBuilder = new BasicOCSPRespBuilder(respID);
			Extension nonceExtn = request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
			if(nonceExtn != null)
			{
				basicOcspBuilder.setResponseExtensions(new Extensions(nonceExtn));
			}
			
			for(int i = 0; i < n; i++)
			{
				Req req = requestList[i];
				CertificateID certID =  req.getCertID();
				String certIdHashAlgo = certID.getHashAlgOID().getId();
				HashAlgoType hashAlgo = HashAlgoType.getHashAlgoType(certIdHashAlgo);
				if(hashAlgo == null)
				{
					LOG.warn("unknown CertID.hashAlgorithm {}", certIdHashAlgo);
					return createUnsuccessfullOCSPResp(CSPResponseStatus.malformedRequest);
				}
				
				CertStatusInfo certStatusInfo = null;
				
				for(CertStatusStore store : certStatusStores)
				{
					try {
						certStatusInfo = store.getCertStatus(
								hashAlgo, certID.getIssuerNameHash(), certID.getIssuerKeyHash(),
								certID.getSerialNumber(), includeCertHash);
						if(certStatusInfo.getCertStatus() != CertStatus.ISSUER_UNKNOWN)
						{
							break;
						}
					} catch (CertStatusStoreException e) {
						LOG.error("answer() queryExecutor.getCertStatus", e);
						return createUnsuccessfullOCSPResp(CSPResponseStatus.tryLater);
					}
				}
				
				// certStatusInfo could not be null in any case, since at least one store is configured 
				Date thisUpdate = certStatusInfo.getThisUpdate();
				if(thisUpdate == null)
				{
					thisUpdate = new Date();
				}
				Date nextUpdate = certStatusInfo.getNextUpdate();
				
				CertificateStatus bcCertStatus = null;
				switch(certStatusInfo.getCertStatus())
				{
					case GOOD:
						break;
					case ISSUER_UNKNOWN:
					case UNKNOWN:
						bcCertStatus = new UnknownStatus();
						break;
					case REVOCATED:
						CertRevocationInfo revInfo = certStatusInfo.getRevocationInfo();
						bcCertStatus = new RevokedStatus(revInfo.getRevocationTime(), revInfo.getReason());
						break;
				}
				
				Extension certHashExtension = null;

				Extensions extensions = null;
				byte[] certHash = certStatusInfo.getCertHash();
				if(includeCertHash && certHash != null)
				{					
					ASN1ObjectIdentifier hashAlgoOid = 
							new ASN1ObjectIdentifier(certStatusInfo.getCertHashAlgo().getOid());
					AlgorithmIdentifier aId = new AlgorithmIdentifier(hashAlgoOid, DERNull.INSTANCE);
					CertHash bcCertHash = new CertHash(aId, certHash);
					
					byte[] encodedCertHash;
					try {
						encodedCertHash = bcCertHash.getEncoded();
					} catch (IOException e) {
						LOG.error("answer() bcCertHash.getEncoded", e);
						return createUnsuccessfullOCSPResp(CSPResponseStatus.internalError);
					}
					
					certHashExtension = new Extension(ISISMTTObjectIdentifiers.id_isismtt_at_certHash,
							false, encodedCertHash);
					
					extensions = new Extensions(certHashExtension);
				}
				
				basicOcspBuilder.addResponse(certID, bcCertStatus, thisUpdate, nextUpdate, extensions);
			}
	
			ConcurrentContentSigner concurrentSigner = responder.getSigner();
			ContentSigner signer = concurrentSigner.borrowContentSigner();
			BasicOCSPResp basicOcspResp;
			try {
				basicOcspResp = basicOcspBuilder.build(signer, 
						new X509CertificateHolder[]{responder.getCertificate()}, new Date());
			} catch (OCSPException e) {
				LOG.error("answer() basicOcspBuilder.build. OCSPException: {}", e.getMessage());
				LOG.debug("answer() basicOcspBuilder.build", e);
				return createUnsuccessfullOCSPResp(CSPResponseStatus.internalError);
			} finally {
				concurrentSigner.returnContentSigner(signer);
			}			

			OCSPRespBuilder ocspRespBuilder = new OCSPRespBuilder();
			try{
				return ocspRespBuilder.build(CSPResponseStatus.successfull.getStatus(), basicOcspResp);
			} catch (OCSPException e) {
				LOG.error("answer() ocspRespBuilder.build. OCSPException: {}", e.getMessage());
				LOG.debug("answer() ocspRespBuilder.build", e);
				return createUnsuccessfullOCSPResp(CSPResponseStatus.internalError);
			}
			
		}catch(Throwable t)
		{
			LOG.error("Throwable. {}: {}", t.getClass().getName(), t.getMessage());
			LOG.debug("Throwable", t);
			return createUnsuccessfullOCSPResp(CSPResponseStatus.internalError);
		}
	}

	private static OCSPResp createUnsuccessfullOCSPResp(CSPResponseStatus status)
	{
		return new OCSPResp(new OCSPResponse(new OCSPResponseStatus(status.getStatus()), null));
	}

	public void setIncludeCertHash(boolean includeCertHash) {
		this.includeCertHash = includeCertHash;
	}

	public void setRequireReqSigned(boolean requireReqSigned) {
		this.requireReqSigned = requireReqSigned;
	}

	public void setCheckReqSignature(boolean checkReqSignature) {
		this.checkReqSignature = checkReqSignature;
	}
	
	public void setSecurityFactory(SecurityFactory securityFactory) {
		this.securityFactory = securityFactory;
	}

	
	
	public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
		this.dataSourceFactory = dataSourceFactory;
	}

	public void setPasswordResolver(PasswordResolver passwordResolver) {
		this.passwordResolver = passwordResolver;
	}



	private static CertificateFactory certFact;
	private static X509Certificate parseCert(String f) throws OCSPResponderException
	{
		try{
			return IoCertUtil.parseCert(f);
		}catch(IOException e)
		{
			throw new OCSPResponderException(e);
		} catch (CertificateException e) {
			throw new OCSPResponderException(e);
		}
	}
	
	private static X509CRL parseCRL(String f) throws OCSPResponderException
	{
		try{
			if(certFact == null)
			{
				certFact = CertificateFactory.getInstance("X.509", "BC");
			}
			return (X509CRL) certFact.generateCRL(new FileInputStream(f));
		}catch(IOException e)
		{
			throw new OCSPResponderException(e);
		} catch (CertificateException e) {
			throw new OCSPResponderException(e);
		} catch (CRLException e) {
			throw new OCSPResponderException(e);
		} catch (NoSuchProviderException e) {
			throw new OCSPResponderException(e);
		}
	}

	public void setConfFile(String confFile) {
		this.confFile = confFile;
	}
}