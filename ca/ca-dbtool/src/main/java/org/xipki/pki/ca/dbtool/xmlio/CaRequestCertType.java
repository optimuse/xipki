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

package org.xipki.pki.ca.dbtool.xmlio;

import javax.xml.stream.XMLStreamException;

import org.xipki.commons.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaRequestCertType extends IdentifidDbObjectType {

    public static final String TAG_ROOT = "reqcert";

    public static final String TAG_RID = "rid";

    public static final String TAG_CID = "cid";

    private Long rid;

    private Long cid;

    public Long getRid() {
        return rid;
    }

    public void setRid(final long rid) {
        this.rid = rid;
    }

    public Long getCid() {
        return cid;
    }

    public void setCid(final long cid) {
        this.cid = cid;
    }

    @Override
    public void validate() throws InvalidDataObjectException {
        super.validate();
        assertNotNull("rid", rid);
        assertNotNull("cid", cid);
    }

    @Override
    public void writeTo(final DbiXmlWriter writer)
    throws InvalidDataObjectException, XMLStreamException {
        ParamUtil.requireNonNull("writer", writer);
        validate();

        writer.writeStartElement(TAG_ROOT);
        writeIfNotNull(writer, TAG_ID, getId());
        writeIfNotNull(writer, TAG_RID, rid);
        writeIfNotNull(writer, TAG_CID, cid);
        writer.writeEndElement();
        writer.writeNewline();
    }

}
