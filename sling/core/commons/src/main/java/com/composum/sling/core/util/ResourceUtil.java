package com.composum.sling.core.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.base.util.AccessControlUtil;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 */
public class ResourceUtil extends org.apache.sling.api.resource.ResourceUtil {

    public static final String PROP_RESOURCE_TYPE =
            SlingConstants.NAMESPACE_PREFIX + ":" + SlingConstants.PROPERTY_RESOURCE_TYPE;
    public static final String PROP_RESOURCE_SUPER_TYPE =
            SlingConstants.NAMESPACE_PREFIX + ":" + SlingConstants.PROPERTY_RESOURCE_SUPER_TYPE;
    public static final String CONTENT_NODE = "jcr:content";

    public static final String TYPE_OAKINDEX = "oak:QueryIndexDefinition";
    public static final String TYPE_FILE = "nt:file";
    public static final String TYPE_LINKED_FILE = "nt:linkedFile";
    public static final String TYPE_RESOURCE = "nt:resource";
    public static final String TYPE_UNSTRUCTURED = "nt:unstructured";

    public static final String TYPE_LOCKABLE = "mix:lockable";
    public static final String TYPE_ORDERABLE = "mix:orderable";
    public static final String TYPE_REFERENCEABLE = "mix:referenceable";

    public static final String PROP_UUID = "jcr:uuid";
    public static final String PROP_TITLE = "jcr:title";
    public static final String PROP_DESCRIPTION = "jcr:description";

    public static final String PROP_DATA = "jcr:data";
    public static final String PROP_MIME_TYPE = "jcr:mimeType";
    public static final String PROP_ENCODING = "jcr:encoding";
    public static final String PROP_PRIMARY_TYPE = "jcr:primaryType";
    public static final String PROP_JCR_CONTENT = "jcr:content";
    public static final String PROP_LAST_MODIFIED = "jcr:lastModified";
    public static final String PROP_FILE_REFERENCE = "fileReference";

    public static String getNameExtension(Resource resource) {
        String extension = null;
        String name = resource.getName();
        if (ResourceUtil.CONTENT_NODE.equals(name)) {
            name = resource.getParent().getName();
        }
        int dot = name.lastIndexOf('.');
        extension = (dot >= 0 ? name.substring(dot + 1).toLowerCase() : "");
        return extension;
    }

    public static boolean isResourceType(Resource resource, String resourceType) {
        if (resource != null) {
            if (resource.isResourceType(resourceType)) {
                return true;
            } else {
                try {
                    final Node node = resource.adaptTo(Node.class);
                    return node != null && node.isNodeType(resourceType);
                } catch (RepositoryException e) {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    public static Resource getResourceType(Resource resource) {
        return resource != null ? getResourceType(resource.getResourceResolver(),
                resource.getResourceType()) : null;
    }

    public static Resource getResourceType(ResourceResolver resolver, String resourceTypeName) {
        Resource resourceType = null;
        if (StringUtils.isNotBlank(resourceTypeName)) {
            if (resourceTypeName.startsWith("/")) {
                resourceType = resolver.getResource(resourceTypeName);
            } else {
                final String[] searchPath = resolver.getSearchPath();
                for (String path : searchPath) {
                    resourceType = resolver.getResource(path + resourceTypeName);
                    if (resourceType != null) {
                        return resourceType;
                    }
                }
            }
        }
        return resourceType;
    }

    public static boolean isResourceType(Resource resource, Pattern pattern) {
        return resource != null && isResourceType(resource.getResourceResolver(), resource.getResourceType(), pattern);
    }

    public static boolean isResourceType(ResourceResolver resolver, String resourceTypeName, Pattern pattern) {
        if (StringUtils.isNotBlank(resourceTypeName)) {
            if (pattern.matcher(resourceTypeName).find()) {
                return true;
            } else {
                Resource resourceType = getResourceType(resolver, resourceTypeName);
                if (resourceType == null) {
                    return false;
                } else {
                    ValueMap values = resourceType.adaptTo(ValueMap.class);
                    String resourceSuperTypeName = values.get(PROP_RESOURCE_SUPER_TYPE, "");
                    return isResourceType(resolver, resourceSuperTypeName, pattern);
                }
            }
        }
        return false;
    }

    public static <T> T getTypeProperty(Resource resource, String name, T defaultValue) {
        T value = getTypeProperty(resource, name, PropertyUtil.getType(defaultValue));
        return value != null ? value : defaultValue;
    }

    public static <T> T getTypeProperty(Resource resource, String name, Class<T> type) {
        return resource != null ? getTypeProperty(resource.getResourceResolver(),
                resource.getResourceType(), name, type) : null;
    }

    public static <T> T getTypeProperty(ResourceResolver resolver, String resourceTypeName,
                                        String name, Class<T> type) {
        T value = null;
        Resource resourceType = getResourceType(resolver, resourceTypeName);
        if (resourceType != null) {
            ValueMap values = resourceType.adaptTo(ValueMap.class);
            value = values.get(name, type);
            if (value == null) {
                String resourceSuperTypeName = values.get(PROP_RESOURCE_SUPER_TYPE, "");
                value = getTypeProperty(resolver, resourceSuperTypeName, name, type);
            }
        }
        return value;
    }

    public static Resource getOrCreateResource(ResourceResolver resolver, String path)
            throws RepositoryException {
        return getOrCreateResource(resolver, path, null);
    }

    public static Resource getOrCreateResource(ResourceResolver resolver, String path, String primaryTypes)
            throws RepositoryException {
        Resource resource = resolver.getResource(path);
        if (resource == null) {
            int lastPathSegment = path.lastIndexOf('/');
            String parentPath = "/";
            String name = path;
            if (lastPathSegment >= 0) {
                name = path.substring(lastPathSegment + 1);
                parentPath = path.substring(0, lastPathSegment);
                if (StringUtils.isBlank(parentPath)) {
                    parentPath = "/";
                }
            }
            int lastTypeSegment;
            String parentTypes = primaryTypes;
            String type = primaryTypes;
            if (primaryTypes != null && (lastTypeSegment = primaryTypes.lastIndexOf('/')) >= 0) {
                type = primaryTypes.substring(lastTypeSegment + 1);
                parentTypes = primaryTypes.substring(0, lastTypeSegment);
            }
            Resource parent = getOrCreateResource(resolver, parentPath, parentTypes);
            if (parent != null) {
                Node node = parent.adaptTo(Node.class);
                if (node != null) {
                    if (StringUtils.isNotBlank(type)) {
                        node.addNode(name, type);
                    } else {
                        node.addNode(name);
                    }
                }
                resource = parent.getChild(name);
            }
        }
        return resource;
    }

    public static boolean containsPath(List<Resource> collection, Resource resource) {
        return containsPath(collection, resource.getPath());
    }

    public static boolean containsPath(List<Resource> collection, String path) {
        for (Resource item : collection) {
            if (item.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the resources child resource, creates this child if not existing.
     *
     * @param resource     the resource to extend
     * @param relPath      the path to the requested child resource
     * @param primaryTypes the 'path' of primary types for the new nodes (optional, can be 'null')
     * @return the requested child
     */
    public static Resource getOrCreateChild(Resource resource, String relPath, String primaryTypes)
            throws RepositoryException {
        Resource child = null;
        if (resource != null) {
            ResourceResolver resolver = resource.getResourceResolver();
            String path = resource.getPath();
            while (relPath.startsWith("/")) {
                relPath = relPath.substring(1);
            }
            if (StringUtils.isNotBlank(relPath)) {
                path += "/" + relPath;
            }
            child = getOrCreateResource(resolver, path, primaryTypes);
        }
        return child;
    }

    /**
     * Checks the access control policies for enabled changes (node creation and property change).
     *
     * @param resource
     * @param relPath
     * @return
     * @throws RepositoryException
     */
    public static boolean isWriteEnabled(Resource resource, String relPath) throws RepositoryException {

        ResourceResolver resolver = resource.getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        AccessControlManager accessManager = AccessControlUtil.getAccessControlManager(session);

        String resourcePath = resource.getPath();
        Privilege[] addPrivileges = new Privilege[]{
                accessManager.privilegeFromName(Privilege.JCR_ADD_CHILD_NODES)
        };
        boolean result = accessManager.hasPrivileges(resourcePath, addPrivileges);

        if (StringUtils.isNotBlank(relPath)) {
            if (!resourcePath.endsWith("/")) {
                resourcePath += "/";
            }
            resourcePath += relPath;
        }
        Privilege[] changePrivileges = new Privilege[]{
                accessManager.privilegeFromName(Privilege.JCR_MODIFY_PROPERTIES)
        };
        try {
            result = result && accessManager.hasPrivileges(resourcePath, changePrivileges);
        } catch (PathNotFoundException pnfex) {
            // ok, let's create it
        }

        return result;
    }

    /**
     * Returns 'true' is this resource represents a 'file' witch can be displayed (a HTML file).
     */
    public static boolean isRenderableFile(Resource resource) {
        boolean result = false;
        try {
            Node node = resource.adaptTo(Node.class);
            if (node != null) {
                NodeType type = node.getPrimaryNodeType();
                if (ResourceUtil.TYPE_FILE.equals(type.getName())) {
                    String resoureName = resource.getName();
                    result = resoureName.toLowerCase().endsWith(LinkUtil.EXT_HTML);
                }
            }
        } catch (RepositoryException e) {
            // ok, not renderable
        }
        return result;
    }

    /**
     * Returns 'true' is this resource represents a 'file'.
     */
    public static boolean isFile(Resource resource) {
        Node node = resource.adaptTo(Node.class);
        if (node != null) {
            try {
                NodeType type = node.getPrimaryNodeType();
                if (type != null) {
                    String typeName = type.getName();
                    switch (typeName) {
                        case TYPE_FILE:
                            return true;
                        case TYPE_RESOURCE:
                        case TYPE_UNSTRUCTURED:
                            try {
                                Property mimeType = node.getProperty(PROP_MIME_TYPE);
                                if (mimeType != null && StringUtils.isNotBlank(mimeType.getString())) {
                                    node.getProperty(ResourceUtil.PROP_DATA);
                                    // PathNotFountException if not present
                                    return true;
                                }
                            } catch (PathNotFoundException pnfex) {
                                // ok, was a check only
                            }
                            break;
                    }
                }
            } catch (RepositoryException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static Resource getDataResource(Resource resource) {
        Node node = resource.adaptTo(Node.class);
        if (node != null) {
            try {
                try {
                    node.getProperty(ResourceUtil.PROP_DATA);
                    return resource;
                } catch (PathNotFoundException pnfex) {
                    Node contentNode = node.getNode(CONTENT_NODE);
                    contentNode.getProperty(PROP_DATA);
                    return resource.getChild(CONTENT_NODE);
                }
            } catch (RepositoryException rex) {
                // ok, property doesn't exist
            }
        }
        return null;
    }

    public static Binary getBinaryData(Resource resource) {
        return PropertyUtil.getBinaryData(resource.adaptTo(Node.class));
    }
}
