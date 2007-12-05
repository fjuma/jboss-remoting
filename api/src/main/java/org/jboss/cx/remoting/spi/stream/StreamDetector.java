package org.jboss.cx.remoting.spi.stream;

/**
 *
 */
public interface StreamDetector {
    /**
     * Detect a stream type.  If the candidate is a stream type recognized by this detector,
     * return the class of the factory to use.  The factory class should have a no-arg constructor.
     *
     * @param candidate the candidate object
     * @return the {@code Class} of the stream serializer factory, or {@code null} if this object is not recognized
     */
    <T> Class<? extends StreamSerializerFactory> detectStream(T candidate); 
}