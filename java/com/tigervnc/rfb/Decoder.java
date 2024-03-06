/* Copyright (C) 2002-2005 RealVNC Ltd.  All Rights Reserved.
 * Copyright (C) 2019 Brian P. Hinz
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

import com.tigervnc.rdr.InStream;
import com.tigervnc.rdr.OutStream;

import static com.tigervnc.rfb.Encodings.encodingCopyRect;

@SuppressWarnings("ALL")
abstract public class Decoder {
	
	Decoder(int flags) {
		
		this.flags = flags;
	}
	
	public static class DecoderFlags {
		// A constant for decoders that don't need anything special
		public static int DecoderPlain = 0;
		// All rects for this decoder must be handled in order
		public static int DecoderOrdered = 1;
		// Only some of the rects must be handled in order,
		// see doesRectsConflict()
		public static int DecoderPartiallyOrdered = 1 << 1;
	}
	
	public Decoder(int flags) {
		this.flags = flags;
	}
	
	abstract public void readRect(Rect r, InStream is,
	                              ServerParams server, OutStream os);
	
	abstract public void decodeRect(Rect r, Object buffer,
	                                int buflen, ServerParams server,
	                                com.tigervnc.rfb.ModifiablePixelBuffer pb);
	
	public void getAffectedRegion(Rect rect, Object buffer,
	                              int buflen, ServerParams server,
	                              Region region) {
		region.reset(rect);
	}
	
	public boolean doRectsConflict(Rect ignoredRectA, Object ignoredBufferA,
	                               int ignoredBuflenA, Rect ignoredRectB,
	                               Object ignoredBufferB, int ignoredBuflenB,
	                               ServerParams ignoredServer) {
		return false;
	}
	
	static public boolean supported(int encoding) {
		return switch(encoding) {
			case com.tigervnc.rfb.Encodings.encodingRaw, encodingCopyRect, com.tigervnc.rfb.Encodings.encodingRRE, com.tigervnc.rfb.Encodings.encodingHextile, com.tigervnc.rfb.Encodings.encodingZRLE, com.tigervnc.rfb.Encodings.encodingTight ->
					true;
			default -> false;
		};
	}
	
	 static com.tigervnc.rfb.Decoder createDecoder(int encoding) {
		Decoder result;
		switch(encoding) {
			case Encodings.encodingRaw:
				result = new com.tigervnc.rfb.Decoder() {
					/**
					 * @param r
					 * @param is
					 * @param server
					 * @param os
					 */
					@Override
					public void readRect(final com.tigervnc.rfb.Rect r, final com.tigervnc.rdr.InStream is, final com.tigervnc.rfb.ServerParams server, final com.tigervnc.rdr.OutStream os) {
					
					}
					
					/**
					 * @param r
					 * @param buffer
					 * @param buflen
					 * @param server
					 * @param pb
					 */
					@Override
					public void decodeRect(final com.tigervnc.rfb.Rect r, final Object buffer, final int buflen, final com.tigervnc.rfb.ServerParams server, final com.tigervnc.rfb.ModifiablePixelBuffer pb) {
					
					}
				};
				break;
			case com.tigervnc.rfb.Encodings.encodingCopyRect:
				result = new com.tigervnc.rfb.Decoder() {
					/**
					 * @param r
					 * @param is
					 * @param server
					 * @param os
					 */
					@Override
					public void readRect(final com.tigervnc.rfb.Rect r, final com.tigervnc.rdr.InStream is, final com.tigervnc.rfb.ServerParams server, final com.tigervnc.rdr.OutStream os) {
					
					}
					
					/**
					 * @param r
					 * @param buffer
					 * @param buflen
					 * @param server
					 * @param pb
					 */
					@Override
					public void decodeRect(final com.tigervnc.rfb.Rect r, final Object buffer, final int buflen, final com.tigervnc.rfb.ServerParams server, final com.tigervnc.rfb.ModifiablePixelBuffer pb) {
					
					}
				};
				break;
			case Encodings.encodingRRE:
				result = new Decoder();
				break;
			case Encodings.encodingHextile:
				result = new Decoder();
				break;
			case Encodings.encodingZRLE:
				result = new Decoder();
				break;
			case Encodings.encodingTight:
				result = new Decoder();
				break;
			default:
				result = null;
				break;
		}
		return result;
	}
	
	public final int flags;
}