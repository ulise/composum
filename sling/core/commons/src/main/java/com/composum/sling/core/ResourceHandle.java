/*
 * Copyright (c) 2013 IST GmbH Dresden
 * Eisenstuckstraße 10, 01069 Dresden, Germany
 * All rights reserved.
 *
 * Name: ResourceHandle.java
 * Autor: Ralf Wunsch, Mirko Zeibig
 */

package com.composum.sling.core;

import com.composum.sling.core.mapping.MappingRules;
import com.composum.sling.core.util.PropertyUtil;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Pattern;

/**
 * the wrapper to enhance the Sling Resource class
 */
public class ResourceHandle extends ResourceWrapper {

    /** the 'adaptTo' like wrapping helper */
    public static ResourceHandle use(Resource resource) {
        ResourceHandle handle = resource instanceof ResourceHandle
                ? ((ResourceHandle) resource) : new ResourceHandle(resource);
        return handle;
    }

    /** the universal validation test */
    public static boolean isValid(Resource resource) {
        return resource instanceof ResourceHandle
                ? ((ResourceHandle) resource).isValid()
                : resource != null;
    }

    // initialized attributes

    protected final Resource resource;
    protected final ValueMap properties;

    // attributes retrieved on demand

    private transient Boolean valid;
    private transient Node node;
    private transient String path;
    private transient String id;
    private transient String title;

    private transient ResourceHandle contentResource;
    private transient InheritedValues inheritedValues;
    protected boolean useNodeInheritance = false;

    /**
     * creates a new wrapper instance.
     */
    protected ResourceHandle(Resource resource) {
        super(resource);
        this.resource = super.getResource();
        this.properties = ResourceUtil.getValueMap(this.resource);
    }

    /**
     * a resource is valid if not 'null' and resolvable
     */
    public boolean isValid() {
        if (valid == null) {
            valid = (this.resource != null);
            if (valid) {
                valid = (getResourceResolver().getResource(getPath()) != null);
            }
        }
        return valid;
    }

    // property access

    public <T> T getProperty(String key, T defaultValue) {
        Class<T> type = PropertyUtil.getType(defaultValue);
        T value = getProperty(key, type);
        return value != null ? value : defaultValue;
    }

    public <T> T getProperty(String key, Class<T> type) {
        T value = properties.get(key, type);
        return value;
    }

    public String getProperty(String key) {
        return getProperty(key, String.class);
    }

    public ValueMap getProperties() {
        return properties;
    }

    public void setProperty(String name, String value) throws RepositoryException {
        setProperty(name, value, PropertyType.STRING);
    }

    public void setProperty(String name, boolean value) throws RepositoryException {
        setProperty(name, value, PropertyType.BOOLEAN);
    }

    public void setProperty(String name, Calendar value) throws RepositoryException {
        setProperty(name, value, PropertyType.DATE);
    }

    public void setProperty(String name, Object value, int type)
            throws RepositoryException {
        Node node = getNode();
        if (node != null) {
            PropertyUtil.setProperty(node, name, value, type);
        }
    }

    public void setProperty(String name, InputStream input)
            throws RepositoryException {
        Node node = getNode();
        if (node != null) {
            PropertyUtil.setProperty(node, name, input);
        }
    }

    public void setProperty(String name, Iterable<String> values) throws RepositoryException {
        setProperty(name, values, PropertyType.STRING);
    }

    public void setProperty(String name, Iterable<?> values, int type)
            throws RepositoryException {
        Node node = getNode();
        if (node != null) {
            PropertyUtil.setProperty(node, name, values, type);
        }
    }

    // content resource access ('jcr:content' child resource)

    /**
     * Retrieves the 'content' resource of this resource.
     * Normally the content resource is the resource ot the 'jcr:content' subnode
     * if this resource ist not the content resource itself.
     *
     * @return the content resource if present or self
     */
    public ResourceHandle getContentResource() {
        if (contentResource == null) {
            if (ResourceUtil.CONTENT_NODE.equals(getName())) {
                contentResource = this;
            } else {
                contentResource = ResourceHandle.use(this.getChild(ResourceUtil.CONTENT_NODE));
                if (!contentResource.isValid()) {
                    contentResource = this; // fallback to the resource itself if no content exists
                }
            }
        }
        return contentResource;
    }

    public <T> T getContentProperty(String key, Class<T> type) {
        return getContentResource().getProperty(key, type);
    }

    public <T> T getContentProperty(String key, T defaultValue) {
        return getContentResource().getProperty(key, defaultValue);
    }

    // inherited property values

    public void setUseNodeInheritance(boolean nodeInheritance) {
        useNodeInheritance = nodeInheritance;
        inheritedValues = null;
    }

    protected InheritedValues getInheritedValues() {
        if (inheritedValues == null) {
            inheritedValues = new InheritedValues(this, useNodeInheritance);
        }
        return inheritedValues;
    }

    public <T> T getInherited(String key, T defaultValue) {
        Class<T> type = PropertyUtil.getType(defaultValue);
        T value = getInherited(key, type);
        return value != null ? value : defaultValue;
    }

    public <T> T getInherited(String key, Class<T> type) {
        T value = (T) getProperty(key, type);
        if (value == null) {
            value = getInheritedValues().get(key, type);
        }
        return value;
    }

    /**
     * lazy getter for the node of this resource (if present, not useful for synthetic resources)
     *
     * @return the node object or <code>null</code> if not available
     */
    public Node getNode() {
        if (node == null) {
            if (resource != null) {
                node = resource.adaptTo(Node.class);
            }
        }
        return node;
    }

    /**
     * retrieves the primary type of the resources node
     */
    public String getPrimaryType() {
        String result = null;
        Node node = getNode();
        if (node != null) {
            try {
                NodeType type = node.getPrimaryNodeType();
                if (type != null) {
                    result = type.getName();
                }
            } catch (RepositoryException e) {
                // ok, no type (null)
            }
        }
        return result;
    }

    /**
     * check the node type or the resource type
     */
    public boolean isOfType(String type) {
        Node node = getNode();
        if (node != null) {
            try {
                return node.isNodeType(type);
            } catch (RepositoryException e) {
            }
        }
        return isResourceType(type);
    }

    /**
     * Lazy getter for the ID of the resources.
     * The ID is the UUID of the resources node if available otherwise the Base64 encoded path.
     *
     * @return a hopefully useful ID (not <code>null</code>)
     */
    public String getId() {
        if (id == null) {
            if (isValid()) {
                id = getProperty(ResourceUtil.PROP_UUID);
            }
            if (StringUtils.isBlank(id)) {
                id = Base64.encodeBase64String(getPath().getBytes(MappingRules.CHARSET));
            }
        }
        return id;
    }

    /**
     * Retrieves the inherited 'super.toString()' value as an ID.
     */
    public String getStringId() {
        return super.toString();
    }

    @Override
    public String getName() {
        return resource != null ? super.getName() : null;
    }

    public String getTitle() {
        if (title == null) {
            title = getProperty(ResourceUtil.PROP_TITLE, String.class);
            if (StringUtils.isBlank(title)) {
                title = getProperty("title", String.class);
            }
            if (StringUtils.isBlank(title)) {
                title = getName();
            }
        }
        return title;
    }

    @Override
    public String getPath() {
        if (path == null) {
            if (resource != null) {
                path = super.getPath();
                if (path.startsWith("//")) { // AEM 6.1 root elements !?
                    path = path.substring(1);
                }
            }
        }
        return path;
    }

    @Override
    public boolean isResourceType(final String resourceType) {
        return resource != null && super.isResourceType(resourceType);
    }

    @Override
    public String getResourceType() {
        return resource != null ? super.getResourceType() : null;
    }

    public String getResourceName() {
        String name = getName();
        return StringUtils.isNotBlank(name) ? name : getPath();
    }

    public String getResourceTitle() {
        String title = getTitle();
        return StringUtils.isNotBlank(title) ? title : getResourceName();
    }

    @Override
    public ResourceHandle getParent() {
        if (resource != null) {
            Resource parent = super.getParent();
            if (parent == null && isSynthetic()) {
                final String parentPath = getParentPath();
                return getResourceResolver().resolve(parentPath).adaptTo(ResourceHandle.class);
            } else if (parent == null) {
                return null;
            } else {
                return parent.adaptTo(ResourceHandle.class);
            }
        } else {
            return null;
        }
    }

    public ResourceHandle getParent(int distance) {
        ResourceHandle parent = this;
        while (distance > 0 && parent != null && parent.isValid()) {
            parent = parent.getParent();
            distance--;
        }
        return parent;
    }


    /**
     * @return
     */
    public String getParentPath() {
        final String parentPath = ResourceUtil.getParent(getPath());
        return parentPath;
    }


    /**
     * Retrieves a child of this resource or a parent specified by its base path, name pattern and type;
     * for example findUpwards("jcr:content", Pattern.compile("^some.*$"), "sling:Folder").
     */
    public ResourceHandle findUpwards(String basePath, Pattern namePattern, String childType) {
        ResourceHandle current = this;
        while (current != null && current.isValid()) {
            ResourceHandle base = ResourceHandle.use(current.getChild(basePath));
            if (base.isValid()) {
                for (ResourceHandle child : base.getChildrenByType(childType)) {
                    if (namePattern.matcher(child.getName()).matches()) {
                        return child;
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }


    @Override
    public String toString() {
        return isValid() ? super.toString() : ("<invalid: " + resource + ">");
    }

    /**
     * @see ResourceUtil#isSyntheticResource(Resource)
     */
    public boolean isSynthetic() {
        return ResourceUtil.isSyntheticResource(this);
    }

    /**
     * @see org.apache.sling.api.resource.ResourceWrapper#adaptTo(java.lang.Class)
     */
    @Override
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == ResourceHandle.class) {
            return type.cast(this);
        } else {
            return this.resource != null ? super.adaptTo(type) : null;
        }
    }

    /**
     * retrieves all children of a type
     */
    public List<ResourceHandle> getChildrenByType(final String type) {
        final ArrayList<ResourceHandle> children = new ArrayList<>();
        if (this.isValid()) {
            for (final Resource child : this.resource.getChildren()) {
                ResourceHandle handle = ResourceHandle.use(child);
                if (handle.isOfType(type)) {
                    children.add(handle);
                }
            }
        }
        return children;
    }

    /**
     * retrieves all children of a sling:resourceType
     */
    public List<ResourceHandle> getChildrenByResourceType(final String resourceType) {
        final ArrayList<ResourceHandle> children = new ArrayList<>();
        if (this.isValid()) {
            for (final Resource child : this.resource.getChildren()) {
                ResourceHandle handle = ResourceHandle.use(child);
                if (handle.isResourceType(resourceType)) {
                    children.add(handle);
                }
            }
        }
        return children;
    }

    /**
     * Returns 'true' is this resource can be displayed itself.
     */
    public boolean isRenderable() {
        String resourceType = getResourceType();
        if (StringUtils.isBlank(resourceType)) {
            return isRenderableFile();
        }
        return true;
    }

    /**
     * Returns 'true' is this resource represents a 'file' witch can be displayed (a HTML file).
     */
    public boolean isRenderableFile() {
        return ResourceUtil.isRenderableFile(this);
    }

    /**
     * Returns 'true' is this resource represents a 'file' (an asset).
     */
    public boolean isFile() {
        return ResourceUtil.isFile(this);
    }
}
