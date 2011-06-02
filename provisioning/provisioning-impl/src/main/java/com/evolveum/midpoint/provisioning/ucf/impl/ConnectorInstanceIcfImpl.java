/*
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
package com.evolveum.midpoint.provisioning.ucf.impl;

import com.evolveum.midpoint.common.XsdTypeConverter;
import com.evolveum.midpoint.provisioning.integration.identityconnector.converter.GuardedStringToStringConverter;
import com.evolveum.midpoint.provisioning.ucf.api.Change;
import com.evolveum.midpoint.provisioning.ucf.api.CommunicationException;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.Operation;
import com.evolveum.midpoint.provisioning.ucf.api.ResultHandler;
import com.evolveum.midpoint.provisioning.ucf.api.Token;
import com.evolveum.midpoint.schema.processor.Definition;
import com.evolveum.midpoint.schema.processor.ResourceObject;
import com.evolveum.midpoint.schema.processor.ResourceObjectAttribute;
import com.evolveum.midpoint.schema.processor.ResourceObjectAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.processor.Schema;
import com.evolveum.midpoint.xml.ns._public.common.common_1.DiagnosticsMessageType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceTestResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.TestResultType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;
import java.util.Collection;
import java.util.HashMap;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfo.Flags;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.common.security.GuardedString;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/**
 * Implementation of ConnectorInstance for ICF connectors.
 * 
 * This class implements the ConnectorInstance interface. The methods are
 * converting the data from the "midPoint semantics" as seen by the 
 * ConnectorInstance interface to the "ICF semantics" as seen by the ICF 
 * framework.
 * 
 * @author Radovan Semancik
 */
public class ConnectorInstanceIcfImpl implements ConnectorInstance {

	private static final String PASSWORD_ATTRIBUTE_NAME = "__PASSWORD__";
	private static final String ACCOUNT_OBJECTCLASS_LOCALNAME = "AccountObjectClass";
	private static final String GROUP_OBJECTCLASS_LOCALNAME = "GroupObjectClass";
	private static final String CUSTOM_OBJECTCLASS_PREFIX = "Custom";
	private static final String CUSTOM_OBJECTCLASS_SUFFIX = "ObjectClass";
	
	ConnectorFacade connector;
	ResourceType resource;

	public ConnectorInstanceIcfImpl(ConnectorFacade connector, ResourceType resource) {
		this.connector = connector;
		this.resource = resource;
	}
	
	private String getSchemaNamespace() {
		return resource.getNamespace();
	}

	/**
	 * Retrieves schema from the resource.
	 * 
	 * Transforms native ICF schema to the midPoint representation.
	 * 
	 * @return midPoint resource schema.
	 * @throws CommunicationException 
	 */
	@Override
	public Schema fetchResourceSchema() throws CommunicationException {

		// Fetch the schema from the connector (which actually gets that from the resource).
		org.identityconnectors.framework.common.objects.Schema icfSchema = connector.schema();
		
		// New instance of midPoint schema object
		Schema mpSchema = new Schema(getSchemaNamespace());
		Set<Definition> definitions = mpSchema.getDefinitions();

		// Let's convert every objectclass in the ICF schema ...
		Set<ObjectClassInfo> objectClassInfoSet = icfSchema.getObjectClassInfo();
		for (ObjectClassInfo objectClassInfo : objectClassInfoSet) {

			// "Flat" ICF object class names needs to be mapped to QNames
			QName objectClassXsdName = objectClassToQname(objectClassInfo.getType());
			
			// Element names does not really make much sense in Resource
			// Objects as they are not usually used. But for the sake of
			// completeness we are going to generate them.
			// TODO: this may need to be moved to a separate method
			QName objectElementName;
			if (ObjectClass.ACCOUNT_NAME.equals(objectClassInfo.getType())) {
				objectElementName = new QName(getSchemaNamespace(),"account", SchemaConstants.NS_ICF_SCHEMA_PREFIX);
			} else if (ObjectClass.GROUP_NAME.equals(objectClassInfo.getType())) {
				objectElementName = new QName(getSchemaNamespace(),"group", SchemaConstants.NS_ICF_SCHEMA_PREFIX);
			} else {
				objectElementName = new QName(getSchemaNamespace(), objectClassInfo.getType(), SchemaConstants.NS_ICF_RESOURCE_INSTANCE_PREFIX);
			}

			// ResourceObjectDefinition is a midPpoint way how to represent an object class.
			// The important thing here is the last "type" parameter (objectClassXsdName). The rest is more-or-less cosmetics.
			ResourceObjectDefinition roDefinition = new ResourceObjectDefinition(mpSchema, objectElementName, objectElementName, objectClassXsdName);
			definitions.add(roDefinition);
			
			// The __ACCOUNT__ objectclass in ICF is a default account objectclass. So mark it appropriately.
			if (ObjectClass.ACCOUNT_NAME.equals(objectClassInfo.getType())) {
				roDefinition.setAccountType(true);
				roDefinition.setDefaultAccountType(true);
			}
			
			// Every object has UID in ICF, therefore add it right now
			ResourceObjectAttributeDefinition uidDefinition = new ResourceObjectAttributeDefinition(roDefinition, SchemaConstants.ICFS_UID, SchemaConstants.ICFS_UID, SchemaConstants.XSD_STRING);
			// Make it mandatory
			uidDefinition.setMinOccurs(1);
			uidDefinition.setMaxOccurs(1);
			roDefinition.getDefinitions().add(uidDefinition);
			// Uid is a primary identifier of every object (this is the ICF way)
			roDefinition.getIdentifiers().add(uidDefinition);
			
			// TODO: may need also other annotations
			
			// Let's iterate over all attributes in this object class ...
			Set<AttributeInfo> attributeInfoSet = objectClassInfo.getAttributeInfo();
			for (AttributeInfo attributeInfo : attributeInfoSet) {

				// Default name and type for the attribute: name is takes "as is", type is mapped
				QName attrXsdName = new QName(getSchemaNamespace(),attributeInfo.getName(),SchemaConstants.NS_ICF_RESOURCE_INSTANCE_PREFIX);
				
				QName attrXsdType = null;
				if (GuardedString.class.equals(attributeInfo.getType())) {
					// GuardedString is a special case. It is a ICF-specific type
					// implementing Potemkin-like security. Use a temporary
					// "nonsense" type for now, so this will fail in tests and
					// will be fixed later
					attrXsdType = SchemaConstants.R_PROTECTED_STRING_TYPE;
				} else {
					attrXsdType = XsdTypeConverter.toXsdType(attributeInfo.getType());
				}
				
				// Handle special cases
				if (Name.NAME.equals(attributeInfo.getName())) {
					// this is ICF __NAME__ attribute. It will look ugly in XML and may even cause problems.
					// so convert to something more friendly such as icfs:name
					attrXsdName = SchemaConstants.ICFS_NAME;
				}
				if (PASSWORD_ATTRIBUTE_NAME.equals(attributeInfo.getName())) {
					// Temporary hack. Password should go into credentials, not attributes
					// TODO: fix this
					attrXsdName = SchemaConstants.ICFS_PASSWORD;
				}
				
				// Create ResourceObjectAttributeDefinition, which is midPoint way how to express attribute schema.
				ResourceObjectAttributeDefinition roaDefinition = new ResourceObjectAttributeDefinition(roDefinition, attrXsdName, attrXsdName, attrXsdType);
				roDefinition.getDefinitions().add(roaDefinition);
				
				// Now we are gooing to process flas such as optional and multi-valued
				Set<Flags> flagsSet = attributeInfo.getFlags();
				//System.out.println(flagsSet);

				roaDefinition.setMinOccurs(0);
				roaDefinition.setMaxOccurs(1);				
				for (Flags flags : flagsSet) {
					if (flags == Flags.REQUIRED) {
						roaDefinition.setMinOccurs(1);
					}
					if (flags == Flags.MULTIVALUED) {
						roaDefinition.setMaxOccurs(-1);
					}
				}
				
				// TODO: process also other flags

			}

		}

		return mpSchema;
	}

	/**
	 * Maps ICF native objectclass name to a midPoint QName objctclass name.
	 * 
	 * The mapping is "stateless" - it does not keep any mapping database or
	 * any other state. There is a bi-directional mapping algorithm.
	 * 
	 * TODO: mind the special characters in the ICF objectclass names.
	 */
	private QName objectClassToQname(String icfObjectClassString) {		
			if (ObjectClass.ACCOUNT_NAME.equals(icfObjectClassString)) {
				return new QName(getSchemaNamespace(), ACCOUNT_OBJECTCLASS_LOCALNAME, SchemaConstants.NS_ICF_SCHEMA_PREFIX);
			} else if (ObjectClass.GROUP_NAME.equals(icfObjectClassString)) {
				return new QName(getSchemaNamespace(), GROUP_OBJECTCLASS_LOCALNAME, SchemaConstants.NS_ICF_SCHEMA_PREFIX);
			} else {
				return new QName(getSchemaNamespace(), CUSTOM_OBJECTCLASS_PREFIX + icfObjectClassString + CUSTOM_OBJECTCLASS_SUFFIX, SchemaConstants.NS_ICF_RESOURCE_INSTANCE_PREFIX);
			}
	}

	/**
	 * Maps a midPoint QName objctclass to the ICF native objectclass name.
	 * 
	 * The mapping is "stateless" - it does not keep any mapping database or
	 * any other state. There is a bi-directional mapping algorithm.
	 * 
	 * TODO: mind the special characters in the ICF objectclass names.
	 */
	private ObjectClass objectClassToIcf(QName qnameObjectClass) {
		if (!getSchemaNamespace().equals(qnameObjectClass.getNamespaceURI())) {
			throw new IllegalArgumentException("ObjectClass QName "+qnameObjectClass+" is not in the appropriate namespace for resource "+resource.getName()+"(OID:"+resource.getOid()+"), expected: "+getSchemaNamespace());
		}
		String lname = qnameObjectClass.getLocalPart();
		if (ACCOUNT_OBJECTCLASS_LOCALNAME.equals(lname)) {
			return ObjectClass.ACCOUNT;
		} else if (GROUP_OBJECTCLASS_LOCALNAME.equals(lname)) {
			return ObjectClass.GROUP;
		} else if (lname.startsWith(CUSTOM_OBJECTCLASS_PREFIX) && lname.endsWith(CUSTOM_OBJECTCLASS_SUFFIX)) {
			String icfObjectClassName = lname.substring(CUSTOM_OBJECTCLASS_PREFIX.length(), lname.length()-CUSTOM_OBJECTCLASS_SUFFIX.length());
			return new ObjectClass(icfObjectClassName);
		} else {
			throw new IllegalArgumentException("Cannot recognize objectclass QName "+qnameObjectClass+" for resource "+resource.getName()+"(OID:"+resource.getOid()+"), expected: "+getSchemaNamespace());
		}
	}


	/**
	 * Looks up ICF Uid identifier in a (potentially multi-valued) set of
	 * identifiers. Handy method to convert midPoint identifier style to an
	 * ICF identifier style.
	 * 
	 * @param identifiers midPoint resource object identifiers
	 * @return ICF UID or null
	 */
	private Uid getUid(Set<ResourceObjectAttribute> identifiers) {
		for (ResourceObjectAttribute attr : identifiers) {
			if (attr.getName().equals(SchemaConstants.ICFS_UID)) {
				return new Uid(attr.getValue(String.class));
			}
		}
		return null;
	}
	
	/**
	 * Converts ICF ConnectorObject to the midPoint ResourceObject.
	 * 
	 * All the attributes are mapped using the same way as they are mapped in the
	 * schema (which is actually no mapping at all now).
	 * 
	 * If an optional ResourceObjectDefinition was provided, the resulting
	 * ResourceObject is schema-aware (getDefinition() method works). If no
	 * ResourceObjectDefinition was provided, the object is schema-less.
	 * TODO: this still needs to be implemented.
	 * 
	 * @param co ICF ConnectorObject to convert
	 * @param def ResourceObjectDefinition (from the schema) or null
	 * @return new mapped ResourceObject instance.
	 */
	private ResourceObject convertToResourceObject(ConnectorObject co,ResourceObjectDefinition def) {
		ResourceObject ro = new ResourceObject();
		
		// TODO: use definition
		
		// Uid is always there
		Uid uid = co.getUid();
		ResourceObjectAttribute uidRoa = new ResourceObjectAttribute(SchemaConstants.ICFS_UID);
		uidRoa.setValue(uid.getUidValue());
		ro.getAttributes().add(uidRoa);
		
		for (Attribute icfAttr : co.getAttributes()) {
			QName qname = new QName(getSchemaNamespace(),icfAttr.getName());
			ResourceObjectAttribute roa = new ResourceObjectAttribute(qname);
			List<Object> icfValues = icfAttr.getValue();
			roa.getValues().addAll(icfValues);
			ro.getAttributes().add(roa);
		}
		
		return ro;
	}
	
	@Override
	public ResourceObject fetchObject(QName objectClass, Set<ResourceObjectAttribute> identifiers) throws CommunicationException {
		
		// Get UID from the set of idetifiers
		Uid uid = getUid(identifiers);
		if (uid == null) {
            throw new IllegalArgumentException("Required attribute UID not found in identification set while attempting to fetch object identified by "+identifiers+" from recource "+resource.getName()+"(OID:"+resource.getOid()+")");
        }
		
		ObjectClass icfObjectClass = objectClassToIcf(objectClass);
		if (objectClass == null) {
            throw new IllegalArgumentException("Unable to detemine object class from QName "+objectClass+" while attempting to fetch object identified by "+identifiers+" from recource "+resource.getName()+"(OID:"+resource.getOid()+")");			
		}
		ConnectorObject co = connector.getObject(icfObjectClass,uid,null);
		if (co==null) {
			// Change to a more reasonable error later
			return null;
		}
		ResourceObject ro = convertToResourceObject(co,null);
		return ro;
	}

	@Override
	public Set<ResourceObjectAttribute> addObject(ResourceObject object, Set<Operation> additionalOperations) throws CommunicationException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void modifyObject(Set<ResourceObjectAttribute> identifiers, Set<Operation> changes) throws CommunicationException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void deleteObject(Set<ResourceObjectAttribute> identifiers) throws CommunicationException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Token deserializeToken(String serializedToken) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Token fetchCurrentToken() throws CommunicationException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<Change> fetchChanges(Token lastToken) throws CommunicationException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ResourceTestResultType test() {
		ResourceTestResultType result = new ResourceTestResultType();

		TestResultType testResult = new TestResultType();
		result.setConnectorConnection(testResult);
		try {
			connector.test();
			testResult.setSuccess(true);
		} catch (RuntimeException ex) {
			testResult.setSuccess(false);
			List<JAXBElement<DiagnosticsMessageType>> errorOrWarning = testResult.getErrorOrWarning();
			DiagnosticsMessageType message = new DiagnosticsMessageType();
			message.setMessage(ex.getClass().getName() + ": " + ex.getMessage());
			// TODO: message.setDetails();
			JAXBElement<DiagnosticsMessageType> element = new JAXBElement<DiagnosticsMessageType>(SchemaConstants.I_DIAGNOSTICS_MESSAGE_ERROR, DiagnosticsMessageType.class, message);
			errorOrWarning.add(element);
		}

		return result;
	}

	@Override
	public void search(QName objectClass, final ResultHandler handler) throws CommunicationException {
		
		ObjectClass icfObjectClass = objectClassToIcf(objectClass);
		if (objectClass == null) {
            throw new IllegalArgumentException("Unable to detemine object class from QName "+objectClass+" while attempting to searcg objects in recource "+resource.getName()+"(OID:"+resource.getOid()+")");
		}
		
		ResultsHandler icfHandler = new ResultsHandler() {
            @Override
            public boolean handle(ConnectorObject connectorObject) {
                // Convert ICF-specific connetor object to a generic ResourceObject
                ResourceObject resourceObject = convertToResourceObject(connectorObject,null);
                // .. and pass it to the handler
                return handler.handle(resourceObject);
            }
        };
		
		connector.search(icfObjectClass,null,icfHandler,null);
		
	}
}
