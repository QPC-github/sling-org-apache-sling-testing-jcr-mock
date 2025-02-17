/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.testing.mock.jcr;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.jcr.Binary;
import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RangeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.lock.Lock;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.ItemNameMatcher;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.jackrabbit.commons.iterator.PropertyIteratorAdapter;


/**
 * Mock {@link Node} implementation
 */
class MockNode extends AbstractItem implements Node {

    public MockNode(final ItemData itemData, final Session session) {
        super(itemData, session);
    }

    @Override
    public Node addNode(final String relPath) throws RepositoryException {
        return addNode(relPath, JcrConstants.NT_UNSTRUCTURED);
    }

    @Override
    public Node addNode(final String relPath, final String primaryNodeTypeName) throws RepositoryException {
        String path = makeAbsolutePath(relPath);
        ItemData itemData = ItemData.newNode(path, new MockNodeType(primaryNodeTypeName));
        Node node = new MockNode(itemData, getSession());
        getMockedSession().addItem(itemData);
        node.setProperty(JcrConstants.JCR_PRIMARYTYPE, primaryNodeTypeName);

        // special handling for some node types
        if (StringUtils.equals(primaryNodeTypeName, JcrConstants.NT_FILE)) {
            node.setProperty(JcrConstants.JCR_CREATED, Calendar.getInstance());
            node.setProperty("jcr:createdBy", getMockedSession().getUserID());
        }

        return node;
    }

    @Override
    public Node getNode(final String relPath) throws RepositoryException {
        String path = makeAbsolutePath(relPath);
        return getSession().getNode(path);
    }

    @Override
    public NodeIterator getNodes() throws RepositoryException {
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
                return item.isNode();
            }
        });
        return new NodeIteratorAdapter(items, items.getSize());
    }

    @Override
    public NodeIterator getNodes(final String namePattern) throws RepositoryException {
        final Pattern pattern = Pattern.compile(namePattern);
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
                return item.isNode() && pattern.matcher(item.getName()).matches();
            }
        });
        return new NodeIteratorAdapter(items, items.getSize());
    }

    @Override
    public NodeIterator getNodes(final String[] nameGlobs) throws RepositoryException {
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
                return item.isNode() && ItemNameMatcher.matches(item.getName(), nameGlobs);
            }
        });
        return new NodeIteratorAdapter(items, items.getSize());
    }


    @Override
    public PropertyIterator getProperties() throws RepositoryException {
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
                return item.isProperty();
            }
        });
        return new PropertyIteratorAdapter(items, items.getSize());
    }

    @Override
    public PropertyIterator getProperties(final String namePattern) throws RepositoryException {
        final Pattern pattern = Pattern.compile(namePattern);
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
                return item.isProperty() && pattern.matcher(item.getName()).matches();
            }
        });
        return new PropertyIteratorAdapter(items, items.getSize());
    }

    @Override
    public PropertyIterator getProperties(final String[] nameGlobs) throws RepositoryException {
        RangeIterator items = getMockedSession().listChildren(getPath(), new ItemFilter() {
            @Override
            public boolean accept(final ItemData item) throws RepositoryException {
            return item.isProperty() && ItemNameMatcher.matches(item.getName(), nameGlobs);
            }
        });
        return new PropertyIteratorAdapter(items, items.getSize());
    }

    @Override
    public Property getProperty(final String relPath) throws RepositoryException {
        String path = makeAbsolutePath(relPath);
        return getSession().getProperty(path);
    }

    @Override
    public String getIdentifier() throws RepositoryException {
        return this.itemData.getUuid();
    }

    @Override
    public String getUUID() throws RepositoryException {
        return getIdentifier();
    }

    @Override
    public boolean hasNode(final String relPath) throws RepositoryException {
        String path = makeAbsolutePath(relPath);
        return getSession().nodeExists(path);
    }

    @Override
    public boolean hasNodes() throws RepositoryException {
        return getNodes().hasNext();
    }

    @Override
    public boolean hasProperties() throws RepositoryException {
        return getProperties().hasNext();
    }

    @Override
    public boolean hasProperty(final String relPath) throws RepositoryException {
        String path = makeAbsolutePath(relPath);
        return getSession().propertyExists(path);
    }

    @Override
    public Property setProperty(final String name, final Value value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    public Property setProperty(final String name, final Value[] values) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(values);
        addItemOrRemoveIfValueNull(itemData, values);
        return property;
    }

    @Override
    public Property setProperty(final String name, final String[] values) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(values);
        addItemOrRemoveIfValueNull(itemData, values);
        return property;
    }

    @Override
    public Property setProperty(final String name, final String value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Property setProperty(final String name, final InputStream value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    public Property setProperty(final String name, final boolean value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItem(itemData);
        return property;
    }

    @Override
    public Property setProperty(final String name, final double value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItem(itemData);
        return property;
    }

    @Override
    public Property setProperty(final String name, final long value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItem(itemData);
        return property;
    }

    @Override
    public Property setProperty(final String name, final Calendar value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    public Property setProperty(final String name, final Node value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    public Property setProperty(final String name, final Binary value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    @Override
    public Property setProperty(final String name, final BigDecimal value) throws RepositoryException {
        ItemData itemData = ItemData.newProperty(getPath() + "/" + name);
        Property property = new MockProperty(itemData, getSession());
        property.setValue(value);
        addItemOrRemoveIfValueNull(itemData, value);
        return property;
    }

    /**
     * Adds or overwrites an item and marks the item value as changed.
     * @param itemData Item data
     * @throws RepositoryException
     */
    private void addItem(ItemData itemData) throws RepositoryException {
        getMockedSession().addItem(itemData);
        this.itemData.setIsChanged(true);
    }

    /**
     * Adds or overwrites an item and marks the item value as changed.
     * If the given value is null, the item is removed instead.
     * @param itemData Item data
     * @throws RepositoryException
     */
    private void addItemOrRemoveIfValueNull(ItemData itemData, Object value) throws RepositoryException {
        if (value == null) {
            getMockedSession().removeItem(itemData.getPath());
        }
        else {
            getMockedSession().addItem(itemData);
        }
        this.itemData.setIsChanged(true);
    }

    @Override
    public boolean isNode() {
        return true;
    }

    @Override
    public boolean isNodeType(final String nodeTypeName) throws RepositoryException {
        boolean istype = this.itemData.getNodeType().isNodeType(nodeTypeName);
        if (!istype) {
            // SLING-11786 also check the mixin types
            istype = Arrays.stream(getMixinNodeTypes()).anyMatch(nt -> nt.getName().equals(nodeTypeName));
        }
        return istype;
    }

    @Override
    public NodeType getPrimaryNodeType() throws RepositoryException {
        return this.itemData.getNodeType();
    }

    @Override
    public Item getPrimaryItem() throws RepositoryException {
        // support "jcr:content" node and "jcr:data" property as primary items
        if (hasProperty(JcrConstants.JCR_DATA)) {
            return getProperty(JcrConstants.JCR_DATA);
        } else if (hasNode(JcrConstants.JCR_CONTENT)) {
            return getNode(JcrConstants.JCR_CONTENT);
        } else {
            throw new ItemNotFoundException();
        }
    }

    @Override
    public int hashCode() {
        return itemData.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MockNode) {
            return itemData.equals(((MockNode)obj).itemData);
        }
        return false;
    }

    @Override
    public NodeType[] getMixinNodeTypes() throws RepositoryException {
        try{
            Value[] mixinNames = getProperty(JcrConstants.JCR_MIXINTYPES).getValues();
            return Arrays.stream(mixinNames)
                    .map(value -> {
                        try {
                            return value.getString();
                        } catch (RepositoryException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(name -> {
                        try {
                            return getSession().getWorkspace().getNodeTypeManager().getNodeType(name);
                        } catch (RepositoryException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toArray(NodeType[]::new);
        } catch(PathNotFoundException e) {
            // if there are not already mixin types added, return empty array
            return new NodeType[0];
        }
    }

    @Override
    public void orderBefore(final String srcChildRelPath, final String destChildRelPath) throws RepositoryException {
        Item srcChild = srcChildRelPath == null ? null : getMockedSession().getItem(getPath() + "/" + srcChildRelPath);
        Item destChild = destChildRelPath == null ? null : getMockedSession().getItem(getPath() + "/" + destChildRelPath);
        getMockedSession().orderBefore(srcChild, destChild);
    }

    @Override
    public NodeDefinition getDefinition() throws RepositoryException {
        return new MockNodeDefinition();
    }

    @Override
    public void setPrimaryType(final String primaryNodeTypeName) throws RepositoryException {
        if (StringUtils.isNotBlank(primaryNodeTypeName)) {
            // accept all node types like stated in the MockNodeTypeManager
            this.itemData.setNodeType(new MockNodeType(primaryNodeTypeName));
            setProperty(JcrConstants.JCR_PRIMARYTYPE, primaryNodeTypeName);
        } else {
            throw new NoSuchNodeTypeException("Not accepting blank node types");
        }
    }

    @Override
    public void addMixin(final String mixinName) throws RepositoryException {
        if (StringUtils.isNotBlank(mixinName)) {
            if(!this.hasProperty(JcrConstants.JCR_MIXINTYPES)) {
                String[] mixinNames = new String[]{mixinName};
                setProperty(JcrConstants.JCR_MIXINTYPES, mixinNames);
            } else {
                Value value = this.getSession().getValueFactory().createValue(mixinName);
                Value[] newValues = Stream.concat(
                        Arrays.stream(getProperty(JcrConstants.JCR_MIXINTYPES).getValues()),
                        Stream.of(value)
                ).toArray(Value[]::new);
                this.setProperty(JcrConstants.JCR_MIXINTYPES, newValues);
            }
        } else {
            throw new NoSuchNodeTypeException("Not accepting blank mixin name");
        }
    }

    @Override
    public void removeMixin(final String mixinName) throws RepositoryException {
        if (this.hasProperty(JcrConstants.JCR_MIXINTYPES)) {
            Value[] currentValues = getProperty(JcrConstants.JCR_MIXINTYPES).getValues();
            Value valueToBeRemoved = this.getSession().getValueFactory().createValue(mixinName);
            Value[] newValues = Arrays.stream(currentValues)
                    .filter(value -> !value.equals(valueToBeRemoved))
                    .toArray(Value[]::new);
            this.setProperty(JcrConstants.JCR_MIXINTYPES, newValues);
        } else {
            throw new NoSuchNodeTypeException("Cannot remove blank mixin");
        }
    }

    // --- unsupported operations ---
    @Override
    public Property setProperty(final String name, final Value value, final int type) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Property setProperty(final String name, final Value[] values, final int type) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Property setProperty(final String name, final String[] values, final int type) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Property setProperty(final String name, final String value, final int type) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canAddMixin(final String mixinName) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelMerge(final Version version) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version checkin() throws RepositoryException  {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkout() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void doneMerge(final Version version) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version getBaseVersion() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCorrespondingNodePath(final String workspaceName) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIndex() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lock getLock() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyIterator getReferences() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public VersionHistory getVersionHistory() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean holdsLock() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCheckedOut() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocked() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lock lock(final boolean isDeep, final boolean isSessionScoped) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeIterator merge(final String srcWorkspace, final boolean bestEffort) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restore(final String versionName, final boolean removeExisting) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restore(final Version version, final boolean removeExisting) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restore(final Version version, final String relPath, final boolean removeExisting) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void restoreByLabel(final String versionLabel, final boolean removeExisting) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(final String srcWorkspaceName) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void followLifecycleTransition(final String transition) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getAllowedLifecycleTransistions() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyIterator getReferences(final String name) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeIterator getSharedSet() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyIterator getWeakReferences() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyIterator getWeakReferences(final String name) throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeShare() throws RepositoryException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeSharedSet() throws RepositoryException {
        throw new UnsupportedOperationException();
    }


}
