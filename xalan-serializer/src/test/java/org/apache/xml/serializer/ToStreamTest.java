/**
 * 
 */
package org.apache.xml.serializer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.SAXException;

/**
 * @author adam@discoverygarden.ca
 *
 */
@RunWith(Parameterized.class)
public class ToStreamTest {
    private MockStream mock;
    private MockWriter mock_writer;
    
    private final static char HIGH = 55349;
    private final static char LOW = 57109;
    private final static String ESCAPED_DECIMAL_CODEPOINT = "&#120597;";
    private final static String CHARACTER = "\uD835\uDF15";
    
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"UTF-8", CHARACTER},
            {"UTF-16", CHARACTER},
            {"ASCII", ESCAPED_DECIMAL_CODEPOINT}
        });
    }
    
    @Parameter
    public String encoding;
    @Parameter(1)
    public String representation;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        mock = new MockStream(encoding);
        mock_writer = new MockWriter();
        mock.m_writer = mock_writer;
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }
    
    

    @Test
    public void testCharacters() {
        try {
            mock.characters(new char [] {'a', 'b', HIGH, LOW, 'c'}, 0, 5);
            mock.flushPending();
            assertEquals("Output matched", "ab"+representation+"c", mock_writer.getOutput());
        } catch (SAXException e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testCharactersSplitSurrogates() {
        try {
            mock.characters(new char[] {HIGH}, 0, 1);
            mock.characters(new char[] {LOW}, 0, 1);
            mock.flushPending();
            assertEquals("Output matched", representation, mock_writer.getOutput());
        }
        catch (SAXException e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testCharactersCompleteSurrogate() {
        try {
            mock.characters(new char[] {HIGH, LOW}, 0, 2);
            mock.flushPending();
            assertEquals("Output matched", representation, mock_writer.getOutput());
        }
        catch (SAXException e) {
            fail(e.getMessage());
        }
    }
    
    @Test(expected = SAXException.class)
    public void testCharactersMultipleHigh() throws SAXException {
        mock.characters(new char[] {HIGH,  HIGH,  LOW}, 0, 3);
        mock.flushPending();
    }
    
    @Test(expected = SAXException.class)
    public void testCharactersMultipleLow() throws SAXException {
        mock.characters(new char[] {HIGH,  LOW,  LOW}, 0, 3);
        mock.flushPending();
    }
    
    @Test(expected = SAXException.class)
    public void testCharactersLoneHigh() throws SAXException {
        mock.characters(new char[] {'a', HIGH, 'b'}, 0, 3);
        mock.flushPending();
    }
    
    @Test(expected = SAXException.class)
    public void testCharactersLoneLow() throws SAXException {
        mock.characters(new char[] {'a', LOW, 'b'}, 0, 3);
        mock.flushPending();
    }
    
    private class MockStream extends ToStream {
        public MockStream(final String encoding) {
            // XXX: Using XML stuff, for convenience.
            m_charInfo = CharInfo.getCharInfo(CharInfo.XML_ENTITIES_RESOURCE, Method.XML);
            m_encodingInfo = Encodings.getEncodingInfo(encoding);
            setEscaping(true);
        }

        @Override
        public void addUniqueAttribute(String qName, String value, int flags) throws SAXException {
            // No-op.
        }

        @Override
        public void endDocument() throws SAXException {
            // No-op.
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
            // No-op.
        }
        
        @Override
        protected void charactersRaw(char[] ch, int start, int length) throws SAXException {
            throw new SAXException("Shouldn't be here...");
        }
        
    }
    
    private class MockWriter extends Writer {
        private StringBuffer sb;
        private String output;
        
        public MockWriter() {
            sb = new StringBuffer();
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            sb.append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            output = sb.toString();
        }

        @Override
        public void close() throws IOException {
            sb = null;
        }
        
        public String getOutput() {
            return output;
        }
    }

}
