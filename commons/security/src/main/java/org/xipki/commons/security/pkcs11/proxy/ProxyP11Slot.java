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

package org.xipki.commons.security.pkcs11.proxy;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.X509Cert;
import org.xipki.commons.security.exception.BadAsn1ObjectException;
import org.xipki.commons.security.exception.P11TokenException;
import org.xipki.commons.security.exception.P11UnknownEntityException;
import org.xipki.commons.security.exception.XiSecurityException;
import org.xipki.commons.security.pkcs11.AbstractP11Slot;
import org.xipki.commons.security.pkcs11.P11EntityIdentifier;
import org.xipki.commons.security.pkcs11.P11Identity;
import org.xipki.commons.security.pkcs11.P11MechanismFilter;
import org.xipki.commons.security.pkcs11.P11ObjectIdentifier;
import org.xipki.commons.security.pkcs11.P11SlotIdentifier;
import org.xipki.commons.security.pkcs11.P11SlotRefreshResult;
import org.xipki.commons.security.util.KeyUtil;
import org.xipki.commons.security.util.X509Util;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class ProxyP11Slot extends AbstractP11Slot {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyP11Slot.class);

    private final ProxyP11Module module;

    private final Asn1P11SlotIdentifier asn1SlotId;

    ProxyP11Slot(final ProxyP11Module module, final P11SlotIdentifier slotId,
            final boolean readOnly, final P11MechanismFilter mechanismFilter)
    throws P11TokenException {
        super(module.getName(), slotId, readOnly, mechanismFilter);
        this.module = module;
        this.asn1SlotId = new Asn1P11SlotIdentifier(slotId);
        refresh();
    }

    @Override
    protected P11SlotRefreshResult doRefresh(final P11MechanismFilter mechanismFilter)
    throws P11TokenException {
        P11SlotRefreshResult refreshResult = new P11SlotRefreshResult();

        // mechanisms
        List<Long> mechs = getMechanismsFromServer();
        for (Long mech : mechs) {
            refreshResult.addMechanism(mech);
        }

        // certificates
        List<P11ObjectIdentifier> certIds =
                getObjectIdsFromServer(P11ProxyConstants.ACTION_getCertIdentifiers);
        for (P11ObjectIdentifier certId : certIds) {
            X509Cert cert = getCertificate(certId);
            if (cert != null) {
                refreshResult.addCertificate(certId, cert);
            }
        }

        // keys
        List<P11ObjectIdentifier> keyIds =
                getObjectIdsFromServer(P11ProxyConstants.ACTION_getIdentityIdentifiers);
        for (P11ObjectIdentifier keyId : keyIds) {
            byte[] id = keyId.getId();
            java.security.PublicKey pubKey = null;
            X509Cert cert = refreshResult.getCertForId(id);
            if (cert != null) {
                pubKey = cert.getCert().getPublicKey();
            } else {
                cert = null;
                pubKey = getPublicKey(keyId);
                if (pubKey == null) {
                    LOG.warn("Neither public key nor certificate is associated with private key {}",
                            keyId);
                    continue;
                }
            }

            X509Certificate[] certs = (cert == null) ? null : new X509Certificate[]{cert.getCert()};

            ProxyP11Identity identity = new ProxyP11Identity(this,
                    new P11EntityIdentifier(slotId, keyId), pubKey, certs);
            refreshResult.addIdentity(identity);
        }

        return refreshResult;
    }

    @Override
    public void close() {
    }

    private PublicKey getPublicKey(final P11ObjectIdentifier objectId)
    throws P11UnknownEntityException, P11TokenException {
        P11EntityIdentifier entityId = new P11EntityIdentifier(slotId, objectId);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_getPublicKey,
                new Asn1P11EntityIdentifier(entityId));
        if (resp == null) {
            return null;
        }

        SubjectPublicKeyInfo pkInfo = SubjectPublicKeyInfo.getInstance(resp);
        try {
            return KeyUtil.generatePublicKey(pkInfo);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new P11TokenException("could not generate Public Key from SubjectPublicKeyInfo:"
                    + ex.getMessage(), ex);
        }
    }

    private X509Cert getCertificate(final P11ObjectIdentifier certId) throws P11TokenException {
        P11EntityIdentifier entityId = new P11EntityIdentifier(slotId, certId);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_getCertificate,
                new Asn1P11EntityIdentifier(entityId));
        if (resp == null) {
            return null;
        }

        try {
            byte[] encoded = resp.toASN1Primitive().getEncoded();
            return new X509Cert(X509Util.parseCert(encoded), encoded);
        } catch (CertificateException | IOException ex) {
            throw new P11TokenException("could not parse certificate:" + ex.getMessage(), ex);
        }
    }

    @Override
    public int removeObjects(final byte[] id, final String label) throws P11TokenException {
        if ((id == null || id.length == 0) && StringUtil.isBlank(label)) {
            throw new IllegalArgumentException("at least one of id and label must not be null");
        }

        Asn1RemoveObjectsParams params = new Asn1RemoveObjectsParams(slotId, id, label);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_removeObjects, params);
        try {
            return Asn1Util.getInteger(resp).intValue();
        } catch (BadAsn1ObjectException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }
    }

    @Override
    protected void doRemoveIdentity(final P11ObjectIdentifier objectId) throws P11TokenException {
        Asn1P11EntityIdentifier asn1EntityId = new Asn1P11EntityIdentifier(slotId, objectId);
        module.send(P11ProxyConstants.ACTION_removeIdentity, asn1EntityId);
    }

    @Override
    protected void doAddCert(final P11ObjectIdentifier objectId, final X509Certificate cert)
    throws P11TokenException, XiSecurityException {
        Asn1EntityIdAndCert asn1 = new Asn1EntityIdAndCert(
                new P11EntityIdentifier(slotId, objectId), cert);
        module.send(P11ProxyConstants.ACTION_addCert, asn1);
    }

    @Override
    protected void doRemoveCerts(final P11ObjectIdentifier objectId) throws P11TokenException {
        Asn1P11EntityIdentifier asn1EntityId = new Asn1P11EntityIdentifier(slotId, objectId);
        module.send(P11ProxyConstants.ACTION_removeCerts, asn1EntityId);
    }

    @Override
    protected P11Identity doGenerateRSAKeypair(final int keysize, final BigInteger publicExponent,
            final String label) throws P11TokenException {
        Asn1GenRSAKeypairParams asn1 = new Asn1GenRSAKeypairParams(asn1SlotId, label, keysize,
                publicExponent);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_genKeypair_RSA, asn1);
        return parseGenerateKeypairResult(resp);
    }

    @Override
    // CHECKSTYLE:OFF
    protected P11Identity doGenerateDSAKeypair(final BigInteger p, final BigInteger q,
            final BigInteger g, final String label) throws P11TokenException {
    // CHECKSTYLE:ON
        Asn1GenDSAKeypairParams asn1 = new Asn1GenDSAKeypairParams(asn1SlotId, label, p, q, g);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_genKeypair_DSA, asn1);
        return parseGenerateKeypairResult(resp);
    }

    @Override
    protected P11Identity doGenerateECKeypair(final ASN1ObjectIdentifier curveId,
            final String label) throws P11TokenException {
        Asn1GenECKeypairParams asn1 = new Asn1GenECKeypairParams(asn1SlotId, label, curveId);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_genKeypair_EC, asn1);
        return parseGenerateKeypairResult(resp);
    }

    private P11Identity parseGenerateKeypairResult(final ASN1Encodable resp)
    throws P11TokenException {
        if (resp == null) {
            throw new P11TokenException("server returned no result");
        }
        Asn1P11EntityIdentifier ei;
        try {
            ei = Asn1P11EntityIdentifier.getInstance(resp);
        } catch (BadAsn1ObjectException ex) {
            throw new P11TokenException(
                    "invalid ASN1 object Asn1P11EntityIdentifier: " + ex.getMessage(), ex);
        }
        if (!slotId.equals(ei.getSlotId().getSlotId())) {
            throw new P11TokenException("");
        }
        P11EntityIdentifier entityId = ei.getEntityId();

        PublicKey publicKey = getPublicKey(entityId.getObjectId());
        return new ProxyP11Identity(this, entityId, publicKey, null);
    }

    @Override
    protected void doUpdateCertificate(final P11ObjectIdentifier objectId,
            final X509Certificate newCert) throws XiSecurityException, P11TokenException {
        Asn1EntityIdAndCert asn1 = new Asn1EntityIdAndCert(
                new P11EntityIdentifier(slotId, objectId), newCert);
        module.send(P11ProxyConstants.ACTION_updateCerificate, asn1);
    }

    private List<Long> getMechanismsFromServer() throws P11TokenException {
        Asn1P11SlotIdentifier asn1SlotId = new Asn1P11SlotIdentifier(slotId);
        ASN1Encodable resp = module.send(P11ProxyConstants.ACTION_getMechanisms, asn1SlotId);
        ASN1Sequence seq = requireSequence(resp);
        final int n = seq.size();

        List<Long> mechs = new ArrayList<>(n);
        for ( int i = 0; i < n; i++) {
            long mech = ASN1Integer.getInstance(seq.getObjectAt(i)).getValue().longValue();
            mechs.add(mech);
        }
        return mechs;
    }

    private List<P11ObjectIdentifier> getObjectIdsFromServer(final int action)
    throws P11TokenException {
        Asn1P11SlotIdentifier asn1SlotId = new Asn1P11SlotIdentifier(slotId);
        ASN1Encodable resp = module.send(action, asn1SlotId);

        List<Asn1P11ObjectIdentifier> asn1ObjectIds;
        try {
            asn1ObjectIds = Asn1P11ObjectIdentifiers.getInstance(resp).getObjectIds();
        } catch (BadAsn1ObjectException ex) {
            throw new P11TokenException("bad ASN1 object: " + ex.getMessage(), ex);
        }

        List<P11ObjectIdentifier> objectIds = new ArrayList<>(asn1ObjectIds.size());
        for (Asn1P11ObjectIdentifier asn1Id : asn1ObjectIds) {
            objectIds.add(asn1Id.getObjectId());
        }
        return objectIds;
    }

    private ASN1Sequence requireSequence(final ASN1Encodable response) throws P11TokenException {
        if (!(response instanceof ASN1Sequence)) {
            throw new P11TokenException("response is not ASN1Sequence, but "
                    + response.getClass().getName());
        }
        return (ASN1Sequence) response;
    }

    ProxyP11Module getModule() {
        return module;
    }

}
