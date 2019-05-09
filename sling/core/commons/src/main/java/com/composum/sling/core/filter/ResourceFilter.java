package com.composum.sling.core.filter;

import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.mapping.jcr.ResourceFilterMapping;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.core.filter.NodeTypeFilters.NODE_TYPE_PREFIX;

/**
 * A ResourceFilter is useful to describe a general way to define scopes in resource hierarchy.
 * Such a filter accepts only resources which properties are matching to filter patterns.
 * These filters can be combined in filter sets with various combination rules.
 * <p>
 * Do not implement this directly, but extend {@link AbstractResourceFilter}.
 */
public interface ResourceFilter {

    Pattern SIMPLE_ARRAY_PATTERN = Pattern.compile("^([+-])\\[(.*)\\]$");

    /**
     * the core function of a filters says: this resource is appropriate or not
     *
     * @param resource the resource object to check
     * @return 'true' if the resource matches
     */
    boolean accept(Resource resource);

    /**
     * This is a hint for {@link FilterSet}s that signals that
     * the filter primary restricts values (is a blacklist) or not (is a whitelist).
     *
     * @return 'true' if this filter excludes objects
     */
    boolean isRestriction();

    /**
     * This constructs a rebuildable String view of the filter: {@link ResourceFilterMapping#toString(ResourceFilter)}
     * uses this method to construct that view, {@link ResourceFilterMapping#fromString(String)} implements the inverse
     * function to reconstruct the filter from the String view.
     *
     * @param builder here the string representation is appended.
     * @see ResourceFilterMapping
     */
    void toString(@Nonnull StringBuilder builder);

    /** the predefined filter instance which accepts each string value */
    ResourceFilter ALL = new AllFilter();

    /** the predefined filter instance for folders */
    ResourceFilter FOLDER = new FolderFilter();


    /**
     * Base class for all ResourceFilters, extend this instead of implementing {@link ResourceFilter} to make it easier to extend the interface.
     */
    abstract class AbstractResourceFilter implements ResourceFilter {

        /**
         * Human readable String representation for debugging purposes: this
         * default implementation uses {@link ResourceFilter#toString(StringBuilder)}.
         */
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            toString(buf);
            return buf.toString();
        }

    }

    /**
     * the 'all enabled' implementation: filters nothing, each value is appropriate
     */
    final class AllFilter extends AbstractResourceFilter {

        @Override
        public boolean accept(Resource resource) {
            return resource != null;
        }

        @Override
        public boolean isRestriction() {
            return false;
        }

        @Override
        public void toString(StringBuilder builder) {
            builder.append("All()");
        }
    }

    /**
     * the 'folder filter' implementation for general tree traversal
     */
    class FolderFilter extends PrimaryTypeFilter {

        public FolderFilter() {
            super(new StringFilter.WhiteList("^(sling|nt):.*[Ff]older$"));
        }

        @Override
        public void toString(StringBuilder builder) {
            builder.append("Folder()");
        }
    }

    /**
     * a general type checking filter
     */
    class TypeFilter extends AbstractResourceFilter {

        @Nonnull
        protected List<String> typeNames;
        protected boolean restriction = false;

        public List<String> getTypeNames() {
            return typeNames;
        }

        public TypeFilter(String names) {
            Matcher matcher = SIMPLE_ARRAY_PATTERN.matcher(names);
            if (matcher.matches()) {
                restriction = "-".equals(matcher.group(1));
                names = matcher.group(2);
            }
            typeNames = Arrays.asList(StringUtils.split(names, ","));
        }

        public TypeFilter(@Nullable List<String> names, boolean restriction) {
            typeNames = names != null ? Collections.unmodifiableList(new ArrayList<>(names)) : Collections.<String>emptyList();
            this.restriction = restriction;
        }

        @Override
        public boolean accept(Resource resource) {
            for (String type : typeNames) {
                if (type.startsWith(NODE_TYPE_PREFIX)) {
                    if (NodeTypeFilters.accept(resource, type)) {
                        return !restriction;
                    }
                } else {
                    if (resource.isResourceType(type)) {
                        return !restriction;
                    }
                }
            }
            return restriction;
        }

        @Override
        public boolean isRestriction() {
            return restriction;
        }

        @Override
        public void toString(StringBuilder builder) {
            builder.append("Type(");
            typeNamesToString(builder);
            builder.append(")");
        }

        /** Writes the typenames in a format parseable with TypeFilter{@link #TypeFilter(List, boolean)}. */
        public void typeNamesToString(StringBuilder builder) {
            builder.append(restriction ? '-' : '+');
            builder.append('[');
            builder.append(StringUtils.join(typeNames, ","));
            builder.append(']');
        }
    }

    /**
     * the abstract base for all filters which are using string patterns (StringFilters)
     */
    abstract class PatternFilter extends AbstractResourceFilter {

        /** the filter instance for the value */
        protected StringFilter filter;

        public StringFilter getFilter() {
            return filter;
        }

        /**
         * Returns the used StringFilter 'restricted' property.
         */
        @Override
        public boolean isRestriction() {
            return filter.isRestriction();
        }

        /**
         * Returns the string representation of the used StringFilter
         */
        @Override
        public void toString(StringBuilder builder) {
            filter.toString(builder);
        }
    }

    /**
     * A ResourceFilter implementation which checks the resources name
     * of a resource using a StringFilter for the name value.
     */
    class NameFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the name value.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public NameFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if their name matches to the used StringFilter
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            return resource != null ? filter.accept(resource.getName()) : false;
        }

        /**
         * Returns the string representation of the filter itself [Name('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("Name(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * A ResourceFilter implementation which checks the resource path
     * of a resource using a StringFilter for the path value.
     */
    class PathFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the resources path.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public PathFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if their path matches to the used StringFilter
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            return resource != null ? filter.accept(resource.getPath()) : false;
        }

        /**
         * Returns the string representation of the filter itself [Path('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("Path(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * A ResourceFilter implementation which checks the 'jcr:primaryType'
     * of a resource using a StringFilter for the type values.
     */
    class PrimaryTypeFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the resources primary type.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public PrimaryTypeFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if their primary node type matches to the used StringFilter
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            String primaryType = ResourceUtil.getPrimaryType(resource);
            if (StringUtils.isNotBlank(primaryType)) return filter.accept(primaryType);
            return false;
        }

        /**
         * Returns the string representation of the filter itself [PrimaryType('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("PrimaryType(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * A ResourceFilter implementation which checks the 'jcr:mixinTypes'
     * of a resource using a StringFilter for the type values.
     */
    class MixinTypeFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the resources mixin types.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public MixinTypeFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if one of the mixin types matches to the used StringFilter
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            if (resource != null) {
                Node node = resource.adaptTo(Node.class);
                if (node != null) {
                    try {
                        NodeType[] mixinTypes = node.getMixinNodeTypes();
                        if (filter.isRestriction()) {
                            for (NodeType mixinType : mixinTypes) {
                                if (!filter.accept(mixinType.getName())) {
                                    return false;
                                }
                            }
                            return true;
                        } else {
                            for (NodeType mixinType : mixinTypes) {
                                if (filter.accept(mixinType.getName())) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    } catch (RepositoryException e) {
                        // ok, its possible that mixin types are not available (synthetic resource)
                    }
                }
            }
            return false;
        }

        /**
         * Returns the string representation of the filter itself [MixinType('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("MixinType(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * A ResourceFilter implementation which checks the 'sling:resourceType'
     * of a resource or its content node using a StringFilter for the type values.
     */
    class ResourceTypeFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the resources sling component type.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public ResourceTypeFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if their sling resource type matches to the used StringFilter;
         * uses a child 'jcr:content' if no resource type is specified for the resource itself.
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            if (resource != null) {
                String type = resource.getResourceType();
                if (StringUtils.isNotBlank(type)) {
                    return accept(type);
                } else {
                    Resource content = resource.getChild(ResourceUtil.CONTENT_NODE);
                    return accept(content);
                }
            }
            return false;
        }

        protected boolean accept(String resourceType) {
            return StringUtils.isNotBlank(resourceType) && filter.accept(resourceType);
        }

        /**
         * Returns the string representation of the filter itself [ResourceType('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("ResourceType(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * A ResourceFilter implementation which checks the 'jcr:mimeType'
     * of a resource or of its 'jcr:content' child.
     */
    class MimeTypeFilter extends PatternFilter {

        /**
         * The constructor based on a StringFilter for the 'mimeType' property.
         *
         * @param filter the string filter (or a filter set) to use
         */
        public MimeTypeFilter(StringFilter filter) {
            this.filter = filter;
        }

        /**
         * Accepts a resource if a 'mimeType' property exists and matches.
         *
         * @param resource the resource object to check
         * @return 'true', if the filter matches
         */
        @Override
        public boolean accept(Resource resource) {
            if (resource != null) {
                ResourceHandle handle = ResourceHandle.use(resource);
                String mimeType = handle.getProperty(ResourceUtil.PROP_MIME_TYPE);
                if (StringUtils.isNotBlank(mimeType)) {
                    return filter.accept(mimeType);
                } else {
                    Resource content = handle.getChild(ResourceUtil.CONTENT_NODE);
                    return accept(content);
                }
            }
            return false;
        }

        /**
         * Returns the string representation of the filter itself [MimeType('filter')]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append("MimeType(");
            super.toString(builder);
            builder.append(")");
        }
    }

    /**
     * An implementation to combine ResourceFilters to complex rules.
     */
    class FilterSet extends AbstractResourceFilter {

        /**
         * the combination rule options:
         * - {@link Rule#and}:   each pattern in the set must accept a value
         * - {@link Rule#or}:    at least one pattern in the set must accept a value
         * - {@link Rule#first}: the first appropriate filter determines the result
         * - {@link Rule#last}:  the last appropriate filter determines the result
         * - {@link Rule#tree}:  used for the filtering of trees of resources - the first is the primary target filter, the next are for intermediate resources that need to be shown just to have all targets show up.
         * - {@link Rule#none}:  no pattern in the set accepts a value. With one argument this is a simple negation.
         */
        public enum Rule {
            /** each pattern in the set must accept a value */
            and,
            /** at least one pattern in the set must accept a value */
            or,
            /**
             * The first appropriate filter determines the result:
             * if it is a {@link ResourceFilter#isRestriction()} (blacklist) that does
             * {@link ResourceFilter#accept(Resource)} a resource, or if it is
             * not a {@link ResourceFilter#isRestriction()} (whitelist) but does
             * {@link ResourceFilter#accept(Resource)} a resource.
             */
            first,
            /**
             * the last appropriate filter determines the result: :
             * if it is a {@link ResourceFilter#isRestriction()} (blacklist) that does
             * {@link ResourceFilter#accept(Resource)} a resource, or if it is
             * not a {@link ResourceFilter#isRestriction()} (whitelist) but does
             * {@link ResourceFilter#accept(Resource)} a resource.
             */
            last,
            /**
             * Used for filtering of trees: the first filter is the primary target filter, the additional filters
             * are filters whose sole goal is to have intermediate resources show up in the tree.
             * It's {@link #accept(Resource)} semantics is like {@link Rule#or},
             * but it's possible to distinguish with {@link #isIntermediate(Resource)} whether the primary
             * target filter was matched, or just one of the intermediate resources filters.
             */
            tree,
            /** no pattern in the set accepts a value. With one argument this is a simple negation. */
            none
        }

        /** the selected combination rule for this filter set */
        protected final Rule rule;
        /** the set of combined filters collected in this set */
        protected final List<ResourceFilter> set;

        /** the cached value for the 'restriction' aspect */
        protected transient Boolean restriction;

        /**
         * Combines a set of filter instances by a combination rule
         *
         * @param rule    the combination rule
         * @param filters the set of combined filters
         */
        public FilterSet(Rule rule, ResourceFilter... filters) {
            this.rule = rule;
            this.set = new ArrayList<>();
            Collections.addAll(this.set, filters);
        }

        /**
         * Combines a set of filter instances by a combination rule
         *
         * @param rule    the combination rule
         * @param filters the set of combined filters
         */
        public FilterSet(Rule rule, List<ResourceFilter> filters) {
            this.rule = rule;
            this.set = filters;
        }

        /**
         * Combines a set of filter instances by a combination rule
         *
         * @param rule    the combination rule
         * @param filters the set of combined filters
         */
        public FilterSet(Rule rule, String... filters) {
            this.rule = rule;
            this.set = new ArrayList<>();
            for (String string : filters) {
                ResourceFilter filter = ResourceFilterMapping.fromString(string);
                if (!ALL.equals(filter)) {
                    set.add(filter);
                }
            }
        }

        /**
         * Returns the operator of the filter set.
         */
        public Rule getRule() {
            return rule;
        }

        /**
         * Returns the list of filter elements of the filter set.
         */
        public List<ResourceFilter> getSet() {
            return set;
        }

        /**
         * Returns 'true' if the resource is found in a 'tree' rule but doesn't match to the target filter
         * (the first filter rule in the set is the target rule in the 'tree' mode).
         * This filter configuration is used for traversing in a tree though the folders up to the target
         * resources. In this case the intermediate resources can be marked as 'intermediate' using
         * this check method.
         */
        public boolean isIntermediate(Resource resource) {
            return rule == Rule.tree && set.size() > 0 && !set.get(0).accept(resource);
        }

        /**
         * Accepts a value if the combination by the selected rule matches to the resource.
         *
         * @param resource the resource object value to check
         * @return 'true', if the resource matches
         */
        @Override
        public boolean accept(Resource resource) {
            switch (rule) {
                case tree:
                case or:
                    for (ResourceFilter filter : set) {
                        if (filter.accept(resource)) {
                            return true;
                        }
                    }
                    return set.size() == 0;
                case and:
                    for (ResourceFilter filter : set) {
                        if (!filter.accept(resource)) {
                            return false;
                        }
                    }
                    return set.size() > 0;
                case none:
                    for (ResourceFilter filter : set) {
                        if (filter.accept(resource)) {
                            return false;
                        }
                    }
                    return true;
                case first:
                    for (ResourceFilter filter : set) {
                        if (filter.accept(resource) && !filter.isRestriction()) {
                            return true;
                        }
                        if (!filter.accept(resource) && filter.isRestriction()) {
                            return false;
                        }
                    }
                    return false;
                case last:
                    for (int i = set.size() - 1; i >= 0; --i) {
                        ResourceFilter filter = set.get(i);
                        if (filter.accept(resource) && !filter.isRestriction()) {
                            return true;
                        }
                        if (!filter.accept(resource) && filter.isRestriction()) {
                            return false;
                        }
                    }
                    return false;
            }
            return isRestriction();
        }

        /**
         * This implements a heuristic whether the filter is a blacklist or whitelist, but <b>caution</b>: for {@link FilterSet}s
         * this is not well defined: please don't rely on this. That is: it's sensible to avoid
         * putting FilterSets into other FilterSet with rules 'first' or 'last' that depend on this - perhaps with the exception of FilterSets with rule 'none' and one argument.
         *
         * @return true for 'or' and 'last' if each of the filters is a restriction,
         * for 'and' and 'first' if one of the filters is a restriction,
         * for 'none' if none of the filters is a restriction, for 'tree' if the first filter is a restriction.
         */
        @Override
        public boolean isRestriction() {
            if (restriction == null) {
                switch (rule) {
                    case or:
                    case last:
                        restriction = true;
                        // return 'true' if each filter in the set is a 'restriction'
                        for (ResourceFilter filter : set) {
                            if (!filter.isRestriction()) {
                                restriction = false;
                                break;
                            }
                        }
                        break;
                    case and:
                    case first:
                        restriction = false;
                        // return 'true' if one filter in the set is a 'restriction'
                        for (ResourceFilter filter : set) {
                            if (filter.isRestriction()) {
                                restriction = true;
                                break;
                            }
                        }
                        break;
                    case none:
                        restriction = true;
                        // return 'false' if one filter in the set is a 'restriction', otherwise true
                        for (ResourceFilter filter : set) {
                            if (filter.isRestriction()) {
                                restriction = false;
                                break;
                            }
                        }
                        break;
                    case tree:
                        restriction = set.size() > 0 ? set.get(0).isRestriction() : true;
                        break;
                }
            }
            return restriction;
        }

        /**
         * Returns the string representation of the filter itself ['rule'{'filter', ...}]
         */
        @Override
        public void toString(StringBuilder builder) {
            builder.append(rule.name());
            builder.append("{");
            for (int i = 0; i < set.size(); ) {
                set.get(i).toString(builder);
                if (++i < set.size()) {
                    builder.append(',');
                }
            }
            builder.append("}");
        }
    }
}
