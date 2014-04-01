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

package org.xipki.ca.server.mgmt.shell;

import java.util.List;

import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.xipki.ca.server.mgmt.PublisherEntry;

@Command(scope = "ca", name = "capub-list", description="List publishers in given CA")
public class CaPublisherListCommand extends CaCommand {
	@Option(name = "-ca",
            description = "Required. CA name",
            required = true)
    protected String           caName;

    @Override
    protected Object doExecute() throws Exception {
		StringBuilder sb = new StringBuilder();		
		
		List<PublisherEntry> entries = caManager.getPublishersForCA(caName);
		if(entries != null && entries.isEmpty() == false)
		{
			sb.append("Publishers for CA " + caName).append("\n");
			for(PublisherEntry entry  : entries)
			{
				sb.append("\t").append(entry.getName()).append("\n");
			}
		}
		else
		{
			sb.append("\tNo publisher for CA " + caName + " is configured");
		}
		
		System.out.println(sb.toString());
		
    	return null;
    }
}