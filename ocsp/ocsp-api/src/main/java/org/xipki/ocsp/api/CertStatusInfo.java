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

package org.xipki.ocsp.api;

import java.util.Date;

public class CertStatusInfo {
	
	private final CertStatus certStatus;
	
	private CertRevocationInfo revocationInfo;
	private HashAlgoType certHashAlgo;
	private byte[] certHash;
	
	private final Date thisUpdate;
	private final Date nextUpdate;

	private CertStatusInfo(CertStatus certStatus, Date thisUpdate, Date nextUpdate)
	{	
		this.certStatus = certStatus;
		this.thisUpdate = thisUpdate;
		this.nextUpdate = nextUpdate;
	}
	
	public static CertStatusInfo getUnknownCertStatusInfo(Date thisUpdate, Date nextUpdate)	
	{
		return new CertStatusInfo(CertStatus.UNKNOWN, thisUpdate, nextUpdate);
	}

	public static CertStatusInfo getIssuerUnknownCertStatusInfo(Date thisUpdate, Date nextUpdate)	
	{
		return new CertStatusInfo(CertStatus.ISSUER_UNKNOWN, thisUpdate, nextUpdate);
	}

	public static CertStatusInfo getGoodCertStatusInfo( 
			HashAlgoType certHashAlgo, byte[] certHash, Date thisUpdate, Date nextUpdate)	
	{
		CertStatusInfo ret = new CertStatusInfo(CertStatus.GOOD, thisUpdate, nextUpdate);
		ret.certHashAlgo = certHashAlgo;
		ret.certHash = certHash;
		return ret;
	}
	
	public static CertStatusInfo getRevocatedCertStatusInfo(CertRevocationInfo revocationInfo, 
			HashAlgoType certHashAlgo, byte[] certHash, Date thisUpdate, Date nextUpdate)	
	{
		if(revocationInfo == null)
		{
			throw new IllegalArgumentException("revocationInfo could not be null");
		}
		CertStatusInfo ret = new CertStatusInfo(CertStatus.REVOCATED, thisUpdate, nextUpdate);
		ret.revocationInfo = revocationInfo;
		ret.certHashAlgo = certHashAlgo;
		ret.certHash = certHash;
		return ret;
	}
	
	
	public Date getThisUpdate()
	{
		return thisUpdate;
	}
	
	public Date getNextUpdate()
	{
		return nextUpdate;
	}
	
	public CertStatus getCertStatus() {
		return certStatus;
	}

	public CertRevocationInfo getRevocationInfo() {
		return revocationInfo;
	}

	public HashAlgoType getCertHashAlgo() {
		return certHashAlgo;
	}

	public byte[] getCertHash() {
		return certHash;
	}
	
}