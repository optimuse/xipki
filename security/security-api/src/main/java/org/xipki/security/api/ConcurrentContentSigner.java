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

package org.xipki.security.api;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.bouncycastle.operator.ContentSigner;

public interface ConcurrentContentSigner {
	
	/**
	 * 
	 * @return the private key if possible. {@code null} may be returned.
	 */
	PrivateKey getPrivateKey();
	
	public void setCertificate(X509Certificate certificate);
	
	public X509Certificate getCertificate();
	
	void initialize(String conf, PasswordResolver passwordResolver) throws SignerException;
	
	public ContentSigner borrowContentSigner() throws NoIdleSignerException;
	
	/**
	 * 
	 * @param timeout timeout in milliseconds, 0 for infinitely
	 * @return
	 * @throws InterruptedException 
	 */
	public ContentSigner borrowContentSigner(int timeout) 
			throws NoIdleSignerException;
	
	public void returnContentSigner(ContentSigner signer);
}