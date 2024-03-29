/* Copyright (C) 2002-2005 RealVNC Ltd.  All Rights Reserved.
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */

package com.tigervnc.rfb;

public class Cursor extends ManagedPixelBuffer {
	
	public Cursor(PixelFormat pf, int w, int h) {
		super(pf, w, h);
		hotspot = new Point(0, 0);
	}
	
	public void setSize(int w, int h) {
		int oldMaskLen = maskLen();
		super.setSize(w, h);
		if(mask == null || maskLen() > oldMaskLen)
			mask = new byte[maskLen()];
	}
	
	public int maskLen() {
		return (width() + 7) / 8 * height();
	}
	
	public Point hotspot;
	public byte[] mask;
}
