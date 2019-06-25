/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.JSGuards;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.JSTargetableNode;
import com.oracle.truffle.js.nodes.access.PropertyNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSUserObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;

@ImportStatic(JSTruffleOptions.class)
@ReportPolymorphism
public abstract class SpecializedNewObjectNode extends JavaScriptBaseNode {
    protected final JSContext context;
    protected final boolean isBuiltin;
    protected final boolean isConstructor;
    protected final boolean isGenerator;
    protected final boolean isAsyncGenerator;
    @Child private JSTargetableNode getPrototypeNode;

    public SpecializedNewObjectNode(JSContext context, boolean isBuiltin, boolean isConstructor, boolean isGenerator, boolean isAsyncGenerator) {
        this.context = context;
        this.isBuiltin = isBuiltin;
        this.isConstructor = isConstructor;
        this.isGenerator = isGenerator;
        this.isAsyncGenerator = isAsyncGenerator;
        this.getPrototypeNode = (!isBuiltin && isConstructor) ? PropertyNode.createProperty(context, null, JSObject.PROTOTYPE) : null;
    }

    public static SpecializedNewObjectNode create(JSContext context, boolean isBuiltin, boolean isConstructor, boolean isGenerator, boolean isAsyncGenerator) {
        return SpecializedNewObjectNodeGen.create(context, isBuiltin, isConstructor, isGenerator, isAsyncGenerator);
    }

    public static SpecializedNewObjectNode create(JSFunctionData functionData) {
        return create(functionData.getContext(), functionData.isBuiltin(), functionData.isConstructor(), functionData.isGenerator(), functionData.isAsyncGenerator());
    }

    public final DynamicObject execute(VirtualFrame frame, DynamicObject newTarget) {
        Object prototype = getPrototypeNode != null ? getPrototypeNode.executeWithTarget(frame, newTarget) : Undefined.instance;
        return execute(newTarget, prototype);
    }

    protected abstract DynamicObject execute(DynamicObject newTarget, Object prototype);

    protected Shape getProtoChildShape(Object prototype) {
        CompilerAsserts.neverPartOfCompilation();
        if (JSGuards.isJSObject(prototype)) {
            return JSObjectUtil.getProtoChildShape((DynamicObject) prototype, JSUserObject.INSTANCE, context);
        }
        return null;
    }

    @Specialization(guards = {"!isBuiltin", "isConstructor", "!context.isMultiContext()", "isJSObject(cachedPrototype)", "prototype == cachedPrototype"}, limit = "PropertyCacheLimit")
    public DynamicObject doCachedProto(@SuppressWarnings("unused") DynamicObject target, @SuppressWarnings("unused") DynamicObject prototype,
                    @Cached("prototype") @SuppressWarnings("unused") DynamicObject cachedPrototype,
                    @Cached("getProtoChildShape(prototype)") Shape shape) {
        return JSObject.create(context, shape);
    }

    /** Many different prototypes. */
    @Specialization(guards = {"!isBuiltin", "isConstructor", "!context.isMultiContext()", "isJSObject(prototype)"}, replaces = "doCachedProto")
    public DynamicObject doUncachedProto(@SuppressWarnings("unused") DynamicObject target, DynamicObject prototype,
                    @Cached("create()") BranchProfile slowBranch) {
        return JSObject.create(context, JSObjectUtil.getProtoChildShape(prototype, JSUserObject.INSTANCE, context, slowBranch));
    }

    @Specialization(guards = {"!isBuiltin", "isConstructor", "context.isMultiContext()", "isJSObject(prototype)"})
    public DynamicObject createWithProto(@SuppressWarnings("unused") DynamicObject target, DynamicObject prototype) {
        return JSUserObject.createWithPrototypeInObject(prototype, context);
    }

    @Specialization(guards = {"!isBuiltin", "isConstructor", "!isJSObject(prototype)"})
    public DynamicObject createDefaultProto(DynamicObject target, @SuppressWarnings("unused") Object prototype) {
        // user-provided prototype is not an object
        JSRealm realm = JSRuntime.getFunctionRealm(target, context.getRealm());
        if (isAsyncGenerator) {
            return JSObject.createWithRealm(context, context.getAsyncGeneratorObjectFactory(), realm);
        } else if (isGenerator) {
            return JSObject.createWithRealm(context, context.getGeneratorObjectFactory(), realm);
        }
        return JSUserObject.create(context, realm);
    }

    @Specialization(guards = {"isBuiltin", "isConstructor"})
    static DynamicObject builtinConstructor(@SuppressWarnings("unused") DynamicObject target, @SuppressWarnings("unused") Object proto) {
        return JSFunction.CONSTRUCT;
    }

    @Specialization(guards = {"!isConstructor"})
    public DynamicObject throwNotConstructorFunctionTypeError(DynamicObject target, @SuppressWarnings("unused") Object proto) {
        throw Errors.createTypeErrorNotConstructible(target);
    }
}
