/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.schema;

import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.*;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ExtensionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.GenericObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ValueConstructionType;

/**
 * @author semancik
 *
 */
public class TestJaxbConstruction {
	
	@BeforeSuite
	public void setup() throws SchemaException, SAXException, IOException {
		DebugUtil.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
		PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
	}
	
	@Test
	public void testGenericConstruction() throws JAXBException, SchemaException {
		System.out.println("\n\n ===[ testGenericConstruction ]===\n");
		
		// GIVEN
		PrismContext prismContext = PrismTestUtil.getPrismContext();
		
		GenericObjectType genericType = new GenericObjectType();
		prismContext.adopt(genericType);
		
		PrismObject<GenericObjectType> generic = genericType.asPrismObject();
		assertNotNull("No object definition after adopt", generic.getDefinition());
		
		// WHEN
		ExtensionType extension = new ExtensionType();
		genericType.setExtension(extension);
		
		// THEN
		PrismContainer<Containerable> extensionContainer = generic.findContainer(GenericObjectType.F_EXTENSION);
		assertNotNull("No extension container after setExtension (prism)", extensionContainer);
		assertNotNull("No extension definition after setExtension (prism)", extensionContainer.getDefinition());

		PrismContainerValue<ExtensionType> extensionValueFromJaxb = extension.asPrismContainerValue();
		assertNotNull("No extension container after setExtension (prism)", extensionValueFromJaxb);
		assertNotNull("No extension definition after setExtension (prism)", extensionValueFromJaxb.getParent().getDefinition());

	}
    
    @Test
    public void testExtensionTypeConstruction() throws Exception {
    	System.out.println("\n\n ===[ testExtensionTypeConstruction ]===\n");
    	
    	// GIVEN
    	PrismContext prismContext = PrismTestUtil.getPrismContext();
        
        GenericObjectType object = new GenericObjectType();
        prismContext.adopt(object.asPrismObject(), GenericObjectType.class);

        ExtensionType extension = new ExtensionType();
        object.setExtension(extension);
        
        PrismContainerValue extValue = extension.asPrismContainerValue();
        assertNotNull("No extension definition", extValue.getParent().getDefinition());

        // WHEN
        Item item = extValue.findOrCreateItem(SchemaTestConstants.EXTENSION_STRING_TYPE_ELEMENT);

        // THEN
        assertNotNull(item);
    }
}
