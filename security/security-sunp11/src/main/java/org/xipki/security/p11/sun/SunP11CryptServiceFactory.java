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

package org.xipki.security.p11.sun;

import org.xipki.security.api.P11CryptService;
import org.xipki.security.api.P11CryptServiceFactory;
import org.xipki.security.api.SignerException;

public class SunP11CryptServiceFactory implements P11CryptServiceFactory {

	@Override
	public P11CryptService createP11CryptService(String pkcs11Module,
			char[] password) throws SignerException
	{
		return SunP11CryptService.getInstance(pkcs11Module, password);
	}

}