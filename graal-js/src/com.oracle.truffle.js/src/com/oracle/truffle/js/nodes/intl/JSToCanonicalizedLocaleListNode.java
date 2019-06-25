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
package com.oracle.truffle.js.nodes.intl;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.access.JSHasPropertyNode;
import com.oracle.truffle.js.nodes.array.JSGetLengthNode;
import com.oracle.truffle.js.nodes.cast.JSToObjectNode;
import com.oracle.truffle.js.nodes.cast.JSToStringNode;
import com.oracle.truffle.js.nodes.unary.TypeOfNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.JSString;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.util.IntlUtil;

/**
 * Implementation of ECMA intl402 9.2.1 "CanonicalizeLocaleList" as Truffle node.
 * https://tc39.github.io/ecma402/#sec-canonicalizelocalelist
 */
public abstract class JSToCanonicalizedLocaleListNode extends JavaScriptBaseNode {
    final JSContext context;

    protected JSToCanonicalizedLocaleListNode(JSContext context) {
        this.context = context;
    }

    public static JSToCanonicalizedLocaleListNode create(JSContext context) {
        return JSToCanonicalizedLocaleListNodeGen.create(context);
    }

    public abstract String[] executeLanguageTags(Object value);

    @Specialization()
    protected String[] doRawString(String s) {
        return new String[]{IntlUtil.validateAndCanonicalizeLanguageTag(s)};
    }

    @Specialization(guards = {"isJSString(object)"})
    protected String[] doString(DynamicObject object) {
        String s = JSString.getString(object);
        return doRawString(s);
    }

    @Specialization(guards = {"isJSNull(object)"})
    protected String[] doNull(DynamicObject object) {
        throw Errors.createTypeErrorNotObjectCoercible(object, this);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isUndefined(object)"})
    protected String[] doUndefined(DynamicObject object) {
        return new String[]{};
    }

    @Specialization(guards = {"!isForeignObject(object)", "!isString(object)", "!isJSString(object)", "!isUndefined(object)", "!isJSNull(object)"})
    protected String[] doOtherType(Object object,
                    @Cached("createToObject(context)") JSToObjectNode toObjectNode,
                    @Cached("create(context)") JSGetLengthNode getLengthNode,
                    @Cached JSHasPropertyNode hasPropertyNode,
                    @Cached TypeOfNode typeOfNode,
                    @Cached JSToStringNode toStringNode) {
        List<String> result = new ArrayList<>();
        DynamicObject localeObj = (DynamicObject) toObjectNode.executeTruffleObject(object);
        long len = getLengthNode.executeLong(localeObj);
        for (long k = 0; k < len; k++) {
            if (hasPropertyNode.executeBoolean(localeObj, k)) {
                Object kValue = JSObject.get(localeObj, k);
                String typeOfKValue = typeOfNode.executeString(kValue);
                if (JSRuntime.isNullOrUndefined(kValue) || ((!typeOfKValue.equals("string") && !typeOfKValue.equals("object")))) {
                    throw Errors.createTypeError(Boundaries.stringFormat("String or Object expected in locales list, got %s", typeOfKValue));
                }
                String lt = toStringNode.executeString(kValue);
                String canonicalizedLt = IntlUtil.validateAndCanonicalizeLanguageTag(lt);
                if (!Boundaries.listContains(result, canonicalizedLt)) {
                    Boundaries.listAdd(result, canonicalizedLt);
                }
            }
        }
        return result.toArray(new String[]{});
    }

    @Specialization(guards = {"isForeignObject(object)"})
    protected String[] doForeignType(TruffleObject object,
                    @CachedLibrary(limit = "1") InteropLibrary interop,
                    @Cached TypeOfNode typeOfNode,
                    @Cached JSToStringNode toStringNode) {
        List<String> result = new ArrayList<>();
        long len;
        try {
            len = interop.getArraySize(object);
        } catch (UnsupportedMessageException e) {
            throw Errors.createTypeErrorInteropException(object, e, "getArraySize", this);
        }
        for (long k = 0; k < len; k++) {
            if (interop.isArrayElementReadable(object, k)) {
                Object kValue;
                try {
                    kValue = interop.readArrayElement(object, k);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    throw Errors.createTypeErrorInteropException(object, e, "readArrayElement", this);
                }
                String typeOfKValue = typeOfNode.executeString(kValue);
                if (!typeOfKValue.equals("string") && !typeOfKValue.equals("object")) {
                    throw Errors.createTypeError(Boundaries.stringFormat("String or Object expected in locales list, got %s", typeOfKValue));
                }
                String lt = toStringNode.executeString(kValue);
                String canonicalizedLt = IntlUtil.validateAndCanonicalizeLanguageTag(lt);
                if (!Boundaries.listContains(result, canonicalizedLt)) {
                    Boundaries.listAdd(result, canonicalizedLt);
                }
            }
        }
        return result.toArray(new String[]{});
    }
}
