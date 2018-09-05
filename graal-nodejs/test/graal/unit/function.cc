/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

#define SUITE Function

// Function::NewInstance

EXPORT_TO_JS(NewInstance) {
    Isolate* isolate = args.GetIsolate();
    Local<Function> func = args[0].As<Function>();
    args.GetReturnValue().Set(func->NewInstance(isolate->GetCurrentContext()).ToLocalChecked());
}

EXPORT_TO_JS(NewInstanceWithArguments) {
    Isolate* isolate = args.GetIsolate();
    int argc = args[0].As<Number>()->Value();
    Local<Value>* argv = 0;
    printf("%d\n", args.Length());

    Local<Function> func = args[0].As<Function>();
    args.GetReturnValue().Set(func->NewInstance(isolate->GetCurrentContext(), argc, argv).ToLocalChecked());
}

// Function::SetName

EXPORT_TO_JS(SetName) {
    Local<Function> func = args[0].As<Function>();
    func->SetName(args[1].As<String>());
    args.GetReturnValue().Set(func);
}

// Function::Call

EXPORT_TO_JS(Call) {
    Local<Function> func = args[0].As<Function>();
    Local<Object> recv = args[1].As<Object>();
    int argc = args[2].As<Integer>()->Value();

    Local<Value>* argv = new Local<Value>[argc];
    for (int i = 0; i < argc; i++) {
        argv[i] = args[i + 3];
    }
    args.GetReturnValue().Set(func->Call(recv, argc, argv));
    delete[] argv;
}

#undef SUITE