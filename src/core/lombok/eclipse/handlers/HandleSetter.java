/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.eclipse.handlers;

import static lombok.eclipse.Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
import static lombok.eclipse.Eclipse.annotationTypeMatches;
import static lombok.eclipse.Eclipse.copyAnnotations;
import static lombok.eclipse.Eclipse.copyType;
import static lombok.eclipse.handlers.EclipseHandlerUtil.findAnnotations;
import static lombok.eclipse.handlers.EclipseHandlerUtil.generateNullCheck;
import static lombok.eclipse.handlers.EclipseHandlerUtil.injectMethod;
import static lombok.eclipse.handlers.EclipseHandlerUtil.methodExists;
import static lombok.eclipse.handlers.EclipseHandlerUtil.toEclipseModifier;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.core.AnnotationValues;
import lombok.core.AST.Kind;
import lombok.core.handlers.TransformationsUtil;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;
import org.mangosdk.spi.ProviderFor;

/**
 * Handles the {@code lombok.Setter} annotation for eclipse.
 */
@ProviderFor(EclipseAnnotationHandler.class)
public class HandleSetter implements EclipseAnnotationHandler<Setter> {
	/**
	 * Generates a setter on the stated field.
	 * 
	 * Used by {@link HandleData}.
	 * 
	 * The difference between this call and the handle method is as follows:
	 * 
	 * If there is a {@code lombok.Setter} annotation on the field, it is used and the
	 * same rules apply (e.g. warning if the method already exists, stated access level applies).
	 * If not, the setter is still generated if it isn't already there, though there will not
	 * be a warning if its already there. The default access level is used.
	 */
	public void generateSetterForField(EclipseNode fieldNode, ASTNode pos) {
		for (EclipseNode child : fieldNode.down()) {
			if (child.getKind() == Kind.ANNOTATION) {
				if (annotationTypeMatches(Setter.class, child)) {
					//The annotation will make it happen, so we can skip it.
					return;
				}
			}
		}
		
		createSetterForField(AccessLevel.PUBLIC, fieldNode, fieldNode, pos, false, false);
	}
	
	public boolean handle(AnnotationValues<Setter> annotation, Annotation ast, EclipseNode annotationNode) {
		EclipseNode fieldNode = annotationNode.up();
		if (fieldNode.getKind() != Kind.FIELD) return false;
		Setter annotationInstance = annotation.getInstance();
		AccessLevel level = annotationInstance.value();
		boolean fluent = annotationInstance.fluent();
		
		if (level == AccessLevel.NONE) return true;
		
		return createSetterForField(level, fieldNode, annotationNode, annotationNode.get(), true, fluent);
	}
	
	private boolean createSetterForField(AccessLevel level,
			EclipseNode fieldNode, EclipseNode errorNode, ASTNode pos, boolean whineIfExists, boolean fluent) {
		if (fieldNode.getKind() != Kind.FIELD) {
			errorNode.addError("@Setter is only supported on a field.");
			return true;
		}
		
		FieldDeclaration field = (FieldDeclaration) fieldNode.get();
		String fieldName = new String(field.name);
		
		// TODO: handle clash between fluent & non-fluent setters
		String setterName = fluent ? TransformationsUtil.toFluentSetterName(fieldName) : TransformationsUtil.toSetterName(fieldName);
		
		int modifier = toEclipseModifier(level) | (field.modifiers & ClassFileConstants.AccStatic);
		
		switch (methodExists(setterName, fieldNode, false)) {
		case EXISTS_BY_LOMBOK:
			return true;
		case EXISTS_BY_USER:
			if (whineIfExists) errorNode.addWarning(
					String.format("Not generating %s(%s %s): A method with that name already exists",
					setterName, field.type, fieldName));
			return true;
		default:
		case NOT_EXISTS:
			//continue with creating the setter
		}
		
		MethodDeclaration method = generateSetter((TypeDeclaration) fieldNode.up().get(), field, setterName, modifier, fluent, pos);
		
		injectMethod(fieldNode.up(), method);
		
		return true;
	}
	
	private MethodDeclaration generateSetter(TypeDeclaration parent, FieldDeclaration field, String name,
			int modifier, boolean fluent, ASTNode source) {
		
		int pS = source.sourceStart, pE = source.sourceEnd;
		long p = (long)pS << 32 | pE;
		MethodDeclaration method = new MethodDeclaration(parent.compilationResult);
		Eclipse.setGeneratedBy(method, source);
		method.modifiers = modifier;
		if (fluent) {
			method.returnType = new SingleTypeReference(parent.name, p);
		} else {
			method.returnType = TypeReference.baseTypeReference(TypeIds.T_void, 0);
		}
		method.returnType.sourceStart = pS; method.returnType.sourceEnd = pE;
		Eclipse.setGeneratedBy(method.returnType, source);
		method.annotations = null;
		Argument param = new Argument(field.name, p, copyType(field.type, source), Modifier.FINAL);
		param.sourceStart = pS; param.sourceEnd = pE;
		Eclipse.setGeneratedBy(param, source);
		method.arguments = new Argument[] { param };
		method.selector = name.toCharArray();
		method.binding = null;
		method.thrownExceptions = null;
		method.typeParameters = null;
		method.bits |= ECLIPSE_DO_NOT_TOUCH_FLAG;
		FieldReference thisX = new FieldReference(field.name, p);
		Eclipse.setGeneratedBy(thisX, source);
		thisX.receiver = new ThisReference(source.sourceStart, source.sourceEnd);
		Eclipse.setGeneratedBy(thisX.receiver, source);
		NameReference fieldNameRef = new SingleNameReference(field.name, p);
		Eclipse.setGeneratedBy(fieldNameRef, source);
		Assignment assignment = new Assignment(thisX, fieldNameRef, (int)p);
		assignment.sourceStart = pS; assignment.sourceEnd = pE;
		Eclipse.setGeneratedBy(assignment, source);
		method.bodyStart = method.declarationSourceStart = method.sourceStart = source.sourceStart;
		method.bodyEnd = method.declarationSourceEnd = method.sourceEnd = source.sourceEnd;
		
		Annotation[] nonNulls = findAnnotations(field, TransformationsUtil.NON_NULL_PATTERN);
		Annotation[] nullables = findAnnotations(field, TransformationsUtil.NULLABLE_PATTERN);
		
		List<Statement> statements = new ArrayList<Statement>();
		
		if (nonNulls.length == 0) {
			statements.add(assignment);
		} else {
			Statement nullCheck = generateNullCheck(field, source);
			if (nullCheck != null) statements.add(nullCheck); 
			statements.add(assignment);
		}
		
		if (fluent) {
			Expression thisExpression = new ThisReference(pS, pE);
			Eclipse.setGeneratedBy(thisExpression, source);
			Statement returnStatement = new ReturnStatement(thisExpression, pS, pE);
			Eclipse.setGeneratedBy(returnStatement, source);			
			statements.add(returnStatement);
		}
		
		method.statements = statements.toArray(new Statement[statements.size()]);
		
		Annotation[] copiedAnnotations = copyAnnotations(nonNulls, nullables, source);
		if (copiedAnnotations.length != 0) param.annotations = copiedAnnotations;
		return method;
	}
}
