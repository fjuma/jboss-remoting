package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.Set;
import org.jboss.cx.remoting.BasicMessage;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.core.util.CollectionUtil;

/**
 *
 */
public abstract class AbstractBasicMessage<T> implements BasicMessage<T> {

    private final T body;
    private final ConcurrentMap<Object, Object> messageMap = CollectionUtil.concurrentMap();
    private final List<Header> headers;

    protected AbstractBasicMessage(final T body) {
        this.body = body;
        headers = Collections.synchronizedList(CollectionUtil.<Header>arrayList());
    }

    public T getBody() {
        return body;
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return messageMap;
    }

    public void addHeader(String name, String value) {
        headers.add(new HeaderImpl(name, value));
    }

    public int removeHeaders(String name) {
        int count = 0;
        ListIterator<Header> iterator = headers.listIterator();
        while (iterator.hasNext()) {
            final Header header = iterator.next();
            if (name.equals(header.getName())) {
                iterator.remove();
                count ++;
            }
        }
        return count;
    }

    public String getHeaderValue(String name) {
        final Iterator<Header> iterator = headers.iterator();
        while (iterator.hasNext()) {
            final Header header = iterator.next();
            if (name.equals(header.getName())) {
                return header.getValue();
            }
        }
        return null;
    }

    public Iterable<String> getHeaderValues(String name) {
        List<String> values = CollectionUtil.arrayList();
        final Iterator<Header> iterator = headers.iterator();
        while (iterator.hasNext()) {
            final Header header = iterator.next();
            if (name.equals(header.getName())) {
                values.add(header.getValue());
            }
        }
        return Collections.unmodifiableCollection(values);
    }

    public Iterable<Header> getHeaders() {
        return Collections.unmodifiableCollection(CollectionUtil.arrayList(headers));
    }

    private static final class HeaderImpl implements Header {
        private final String name;
        private final String value;

        public HeaderImpl(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
