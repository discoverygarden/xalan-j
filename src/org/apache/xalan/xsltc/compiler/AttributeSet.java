/*
 * @(#)$Id$
 *
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xalan" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 2001, Sun
 * Microsystems., http://www.sun.com.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 * @author Morten Jorgensen
 *
 */

package org.apache.xalan.xsltc.compiler;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.InstructionList;
import org.apache.xalan.xsltc.compiler.util.AttributeSetMethodGenerator;
import org.apache.xalan.xsltc.compiler.util.ClassGenerator;
import org.apache.xalan.xsltc.compiler.util.ErrorMsg;
import org.apache.xalan.xsltc.compiler.util.MethodGenerator;
import org.apache.xalan.xsltc.compiler.util.Type;
import org.apache.xalan.xsltc.compiler.util.TypeCheckError;

final class AttributeSet extends TopLevelElement {

    // This prefix is used for the method name of attribute set methods
    private static final String AttributeSetPrefix = "$as$";
    
    // Element contents
    private QName            _name;
    private UseAttributeSets _useSets;
    private AttributeSet     _mergeSet;
    private String           _method;
    private boolean          _ignore = false;
    
    /**
     * Returns the QName of this attribute set
     */
    public QName getName() {
	return _name;
    }

    /**
     * Returns the method name of this attribute set. This method name is
     * generated by the compiler (XSLTC)
     */
    public String getMethodName() {
	return _method;
    }

    /**
     * Call this method to prevent a method for being compiled for this set.
     * This is used in case several <xsl:attribute-set...> elements constitute
     * a single set (with one name). The last element will merge itself with
     * any previous set(s) with the same name and disable the other set(s).
     */
    public void ignore() {
	_ignore = true;
    }

    /**
     * Parse the contents of this attribute set. Recognised attributes are
     * "name" (required) and "use-attribute-sets" (optional).
     */
    public void parseContents(Parser parser) {
	
	// Get this attribute set's name
	_name = parser.getQNameIgnoreDefaultNs(getAttribute("name"));
	if ((_name == null) || (_name.equals(EMPTYSTRING))) {
	    ErrorMsg msg = new ErrorMsg(ErrorMsg.UNNAMED_ATTRIBSET_ERR, this);
	    parser.reportError(Constants.ERROR, msg);
	}

	// Get any included attribute sets (similar to inheritance...)
	final String useSets = getAttribute("use-attribute-sets");
	if (useSets.length() > 0) {
	    _useSets = new UseAttributeSets(useSets, parser);
	}

	// Parse the contents of this node. All child elements must be
	// <xsl:attribute> elements. Other elements cause an error.
	final Vector contents = getContents();
	final int count = contents.size();
	for (int i=0; i<count; i++) {
	    SyntaxTreeNode child = (SyntaxTreeNode)contents.elementAt(i);
	    if (child instanceof XslAttribute) {
		parser.getSymbolTable().setCurrentNode(child);
		child.parseContents(parser);
	    }
	    else if (child instanceof Text) {
		// ignore
	    }
	    else {
		ErrorMsg msg = new ErrorMsg(ErrorMsg.ILLEGAL_CHILD_ERR, this);
		parser.reportError(Constants.ERROR, msg);
	    }
	}

	// Point the symbol table back at us...
	parser.getSymbolTable().setCurrentNode(this);
    }

    /**
     * Type check the contents of this element
     */
    public Type typeCheck(SymbolTable stable) throws TypeCheckError {

	if (_ignore) return (Type.Void);

        // _mergeSet Point to any previous definition of this attribute set
	_mergeSet = stable.addAttributeSet(this);

	_method = AttributeSetPrefix + getXSLTC().nextAttributeSetSerial();

	if (_useSets != null) _useSets.typeCheck(stable);
	typeCheckContents(stable);
	return Type.Void;
    }

    /**
     * Compile a method that outputs the attributes in this set
     */
    public void translate(ClassGenerator classGen, MethodGenerator methodGen) {

	if (_ignore) return;

	// Create a new method generator for an attribute set method
	methodGen = new AttributeSetMethodGenerator(_method, classGen);

        // Generate a reference to previous attribute-set definitions with the
        // same name first.  Those later in the stylesheet take precedence.
        if (_mergeSet != null) {
            final ConstantPoolGen cpg = classGen.getConstantPool();
            final InstructionList il = methodGen.getInstructionList();
            final String methodName = _mergeSet.getMethodName();

            il.append(classGen.loadTranslet());
            il.append(methodGen.loadDOM());
            il.append(methodGen.loadIterator());
            il.append(methodGen.loadHandler());
            final int method = cpg.addMethodref(classGen.getClassName(),
                                                methodName, ATTR_SET_SIG);
            il.append(new INVOKESPECIAL(method));
        }

	// Translate other used attribute sets first, as local attributes
	// take precedence (last attributes overrides first)
	if (_useSets != null) _useSets.translate(classGen, methodGen);

	// Translate all local attributes
	final Enumeration attributes = elements();
	while (attributes.hasMoreElements()) {
	    SyntaxTreeNode element = (SyntaxTreeNode)attributes.nextElement();
	    if (element instanceof XslAttribute) {
		final XslAttribute attribute = (XslAttribute)element;
		attribute.translate(classGen, methodGen);
	    }
	}
	final InstructionList il = methodGen.getInstructionList();
	il.append(RETURN);
	
	methodGen.stripAttributes(true);
	methodGen.setMaxLocals();
	methodGen.setMaxStack();
	methodGen.removeNOPs();
	classGen.addMethod(methodGen.getMethod());
    }

    public String toString() {
	StringBuffer buf = new StringBuffer("attribute-set: ");
	// Translate all local attributes
	final Enumeration attributes = elements();
	while (attributes.hasMoreElements()) {
	    final XslAttribute attribute =
		(XslAttribute)attributes.nextElement();
	    buf.append(attribute);
	}
	return(buf.toString());
    }
}
