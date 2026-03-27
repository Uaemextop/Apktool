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
package brut.androlib.res.table;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResEntrySpecTest {

    @Test
    public void testValidEntryNames() {
        assertTrue(ResEntrySpec.isValidEntryName("ic_launcher"));
        assertTrue(ResEntrySpec.isValidEntryName("abc123"));
        assertTrue(ResEntrySpec.isValidEntryName("a"));
        assertTrue(ResEntrySpec.isValidEntryName("my.resource"));
        assertTrue(ResEntrySpec.isValidEntryName("my-resource"));
        assertTrue(ResEntrySpec.isValidEntryName("_private"));
    }

    @Test
    public void testEmptyName() {
        assertFalse(ResEntrySpec.isValidEntryName(""));
    }

    @Test
    public void testDollarSignRejected() {
        assertFalse(ResEntrySpec.isValidEntryName("$avd_hide_password__0"));
        assertFalse(ResEntrySpec.isValidEntryName("$ic_img_notification"));
        assertFalse(ResEntrySpec.isValidEntryName("$sud_fourcolor_progress_bar__0"));
        assertFalse(ResEntrySpec.isValidEntryName("name$with$dollars"));
        assertFalse(ResEntrySpec.isValidEntryName("trailing$"));
    }

    @Test
    public void testInvalidStartCharacter() {
        assertFalse(ResEntrySpec.isValidEntryName("1abc"));
        assertFalse(ResEntrySpec.isValidEntryName(".abc"));
        assertFalse(ResEntrySpec.isValidEntryName("-abc"));
    }
}
