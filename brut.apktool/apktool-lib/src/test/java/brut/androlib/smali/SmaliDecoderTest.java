/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.smali;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SmaliDecoderTest {

    @Test
    public void testResolveSmaliDirName_mainClassesDex() {
        assertEquals("smali", SmaliDecoder.resolveSmaliDirName("classes.dex", 1));
    }

    @Test
    public void testResolveSmaliDirName_multiDexSecondary() {
        assertEquals("smali_classes2", SmaliDecoder.resolveSmaliDirName("classes.dex", 2));
    }

    @Test
    public void testResolveSmaliDirName_multiDexTertiary() {
        assertEquals("smali_classes3", SmaliDecoder.resolveSmaliDirName("classes.dex", 3));
    }

    @Test
    public void testResolveSmaliDirName_nonStandardDexName() {
        assertEquals("smali_custom", SmaliDecoder.resolveSmaliDirName("custom.dex", 1));
    }

    @Test
    public void testResolveSmaliDirName_nestedDexPath() {
        assertEquals("smali_path@to@nested", SmaliDecoder.resolveSmaliDirName("path/to/nested.dex", 1));
    }

    @Test
    public void testResolveSmaliDirName_nestedDexPathMultiDex() {
        assertEquals("smali_path@to@nested2", SmaliDecoder.resolveSmaliDirName("path/to/nested.dex", 2));
    }
}
