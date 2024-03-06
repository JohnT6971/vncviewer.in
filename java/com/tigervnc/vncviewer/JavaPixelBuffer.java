/* Copyright (C) 2012-2016 Brian P. Hinz
 * Copyright (C) 2012 D. R. Commander.  All Rights Reserved.
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

package com.tigervnc.vncviewer;

import java.awt.*;
import java.awt.image.*;
import java.nio.*;

import com.tigervnc.rfb.*;
import com.tigervnc.rfb.Point;

public class JavaPixelBuffer extends PlatformPixelBuffer {
	
	public JavaPixelBuffer(int w, int h) {
		super(getPreferredPF(), w, h,
				getPreferredPF().getColorModel().createCompatibleWritableRaster(w, h));
		java.awt.image.ColorModel cm = format.getColorModel();
		image = new java.awt.image.BufferedImage(cm, data, cm.isAlphaPremultiplied(), null);
		image.setAccelerationPriority(1);
	}
	
	public java.awt.image.WritableRaster getBufferRW(Rect r) {
		synchronized(image) {
			return ((java.awt.image.BufferedImage) image)
					       .getSubimage(r.tl.x, r.tl.y, r.width(), r.height()).getRaster();
		}
	}
	
	public java.awt.image.Raster getBuffer(Rect r) {
		java.awt.Rectangle rect =
				new java.awt.Rectangle(r.tl.x, r.tl.y, r.width(), r.height());
		synchronized(image) {
			return ((java.awt.image.BufferedImage) image).getData(rect);
		}
	}
	
	public void fillRect(Rect r, byte[] pix) {
		java.awt.image.ColorModel cm = format.getColorModel();
		int pixel =
				java.nio.ByteBuffer.wrap(pix).order(format.getByteOrder()).asIntBuffer().get(0);
		java.awt.Color c = new java.awt.Color(cm.getRGB(pixel));
		final java.awt.Image image = null;
		synchronized(image) {
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) image.getGraphics();
			g2.setColor(c);
			g2.fillRect(r.tl.x, r.tl.y, r.width(), r.height());
			g2.dispose();
		}
		
		commitBufferRW(r);
	}
	
	public void copyRect(Rect rect, java.awt.Point move_by_delta) {
		synchronized(image) {
			java.awt.Graphics2D g2 = (java.awt.Graphics2D) image.getGraphics();
			g2.copyArea(rect.tl.x - move_by_delta.x,
					rect.tl.y - move_by_delta.y,
					rect.width(), rect.height(),
					move_by_delta.x, move_by_delta.y);
			g2.dispose();
		}
		
		commitBufferRW(rect);
	}
	
	private static PixelFormat getPreferredPF() {
		java.awt.GraphicsEnvironment ge =
				java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
		java.awt.GraphicsDevice gd = ge.getDefaultScreenDevice();
		java.awt.GraphicsConfiguration gc = gd.getDefaultConfiguration();
		java.awt.image.ColorModel cm = gc.getColorModel();
		int depth = ((cm.getPixelSize() > 24) ? 24 : cm.getPixelSize());
		int bpp = (depth > 16 ? 32 : (depth > 8 ? 16 : 8));
		java.nio.ByteOrder byteOrder = java.nio.ByteOrder.nativeOrder();
		boolean bigEndian = (byteOrder == java.nio.ByteOrder.BIG_ENDIAN ? true : false);
		boolean trueColour = true;
		int redShift = cm.getComponentSize()[0] + cm.getComponentSize()[1];
		int greenShift = cm.getComponentSize()[0];
		int blueShift = 0;
		int redMask = ((int) Math.pow(2, cm.getComponentSize()[2]) - 1);
		int greenMask = ((int) Math.pow(2, cm.getComponentSize()[1]) - 1);
		int blueMmask = ((int) Math.pow(2, cm.getComponentSize()[0]) - 1);
		return new PixelFormat(bpp, depth, bigEndian, trueColour,
				redMask, greenMask, blueMmask,
				redShift, greenShift, blueShift);
	}
	
}