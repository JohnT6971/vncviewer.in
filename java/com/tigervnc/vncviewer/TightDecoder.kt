/* Copyright (C) 2000-2003 Constantin Kaplinsky.  All Rights Reserved.
 * Copyright 2004-2005 Cendio AB.
 * Copyright (C) 2011 D. R. Commander.  All Rights Reserved.
 * Copyright (C) 2011-2019 Brian P. Hinz
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
package com.tigervnc.vncviewer

import com.tigervnc.rdr.InStream
import com.tigervnc.rdr.MemInStream
import com.tigervnc.rdr.OutStream
import com.tigervnc.rdr.ZlibInStream
import com.tigervnc.rfb.JpegDecompressor
import org.w3c.dom.css.Rect
import java.awt.image.WritableRaster
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.*

class TightDecoder<ModifiablePixelBuffer>(isMIME: Boolean) : Base64.Decoder(com.tigervnc.rfb.Decoder.DecoderFlags.DecoderPartiallyOrdered, isMIME) {
    private val zis: Array<ZlibInStream?>

    enum class ZlibInStream {

    }

    private var server: com.tigervnc.rfb.ServerParams? = null

    init {
        zis = arrayOfNulls<ZlibInStream>(4)
        for (i in 0..3) zis[i] = ZlibInStream()
    }

    fun readRect(r: Rect, `is`: InStream,
                 server: com.tigervnc.rfb.ServerParams, os: OutStream) {
        var comp_ctl: Int = `is`.readU8()
        os.writeU8(comp_ctl)

        comp_ctl = comp_ctl shr 4


        // "Fill" compression type.
        if (comp_ctl == tightFill) {
            if (server.pf().is888()) os.copyBytes(`is`, 3)
            else os.copyBytes(`is`, server.pf().bpp / 8)
            return
        }


        // "JPEG" compression type.
        if (comp_ctl == tightJpeg) {
            val len = readCompact(`is`)
            os.writeOpaque32(len)
            os.copyBytes(`is`, len)
            return
        }


        // Quit on unsupported compression type.
        if (comp_ctl > tightMaxSubencoding) throw Exception("TightDecoder: bad subencoding value received")


        // "Basic" compression type.
        var palSize = 0

        if (r.width() > TIGHT_MAX_WIDTH) throw Exception(("TightDecoder: too large rectangle (" + r.width()).toString() + " pixels)")


        // Possible palette
        if ((comp_ctl and tightExplicitFilter) != 0) {
            val filterId: Int = `is`.readU8() and 0xff
            os.writeU8(filterId)

            when (filterId) {
                tightFilterPalette -> {
                    palSize = `is`.readU8() + 1
                    os.writeU32(palSize - 1)

                    if (server.pf().is888()) os.copyBytes(`is`, palSize * 3)
                    else os.copyBytes(`is`, palSize * server.pf().bpp / 8)
                }

                tightFilterGradient -> if (server.pf().bpp === 8) throw Exception("TightDecoder: invalid BPP for gradient filter")
                tightFilterCopy -> {}
                else -> throw Exception("TightDecoder: unknown filter code received")
            }
        }

        val rowSize: Int = if (palSize != 0) {
            if (palSize <= 2) (r.width() + 7) / 8
            else r.width()
        } else if (server.pf().is888()) {
            r.width() * 3
        } else {
            r.width() * server.pf().bpp / 8
        }

        val dataSize: Int = r.height() * rowSize

        if (dataSize < 12) {
            os.copyBytes(`is`, dataSize)
        } else {
            val len = readCompact(`is`)
            os.writeOpaque32(len)
            os.copyBytes(`is`, len)
        }
    }

    fun readCompact(`is`: InStream): Int {
        var b: Byte
        var result: Int

        b = `is`.readU8()
        result = b.toInt() and 0x7F
        if ((b.toInt() and 0x80) != 0) {
            `is`.readU8().(extracted()) { b = it }
            result = result or ((b.toInt() and 0x7F) shl 7)
            if ((b.toInt() and 0x80) != 0) {
                b = `is`.readU8().toByte()
                result = result or ((b.toInt() and 0xFF) shl 14)
            }
        }
        return result
    }

    private fun extracted() {
        also
    }

    fun doRectsConflict(rectA: Rect?,
                        bufferA: Any,
                        buflenA: Int,
                        rectB: Rect?,
                        bufferB: Any,
                        buflenB: Int,
                        server: com.tigervnc.rfb.ServerParams?): Boolean {
        var comp_ctl_a: Byte
        var comp_ctl_b: Byte

        assert(buflenA >= 1)
        assert(buflenB >= 1)
        comp_ctl_a = (bufferA as ByteArray)[0]
        comp_ctl_b = (bufferB as ByteArray)[0]


        // Resets or use of zlib pose the same problem, so merge them
        if ((comp_ctl_a.toInt() and 0x80) == 0x00) comp_ctl_a = (comp_ctl_a.toInt() or (1 shl ((comp_ctl_a.toInt() shr 4) and 0x03))).toByte()
        if ((comp_ctl_b.toInt() and 0x80) == 0x00) comp_ctl_b = (comp_ctl_b.toInt() or (1 shl ((comp_ctl_b.toInt() shr 4) and 0x03))).toByte()

        return ((comp_ctl_a.toInt() and 0x0f) and (comp_ctl_b.toInt() and 0x0f)) != 0
    }

    fun <ModifiablePixelBuffer> decodeRect(r: Rect, buffer: Any?,
                                           buflen: Int, server: com.tigervnc.rfb.ServerParams,
                                           pb: ModifiablePixelBuffer) {
        var buflen = buflen
        server.also { this.server = it }
        var bufptr: ByteBuffer
        val pf: com.tigervnc.rfb.PixelFormat = server.pf()

        var comp_ctl: Int

        bufptr = ByteBuffer.wrap(buffer as ByteArray?)

        assert(buflen >= 1)
        comp_ctl = bufptr.get().toInt() and 0xff
        buflen -= 1


        // Reset zlib streams if we are told by the server to do so.
        for (i in 0..3) {
            if ((comp_ctl and 1) != 0) {
                zis[i].reset()
            }
            comp_ctl = comp_ctl shr 1
        }


        // "Fill" compression type.
        if (comp_ctl == tightFill) {
            if (pf.is888()) {
                val pix = ByteBuffer.allocate(4)

                assert(buflen >= 3)
                pf.bufferFromRGB(pix.duplicate(), bufptr, 1)
                pb.fillRect(pf, r, pix.array())
            } else {
                assert(buflen >= pf.bpp / 8)
                val pix = ByteArray(pf.bpp / 8)
                bufptr[pix]
                pb.fillRect(pf, r, pix)
            }
        } else if (comp_ctl == tightJpeg) {
            var buf: WritableRaster

            val jd: com.tigervnc.rfb.JpegDecompressor = JpegDecompressor()

            assert(buflen >= 4)
            val len = bufptr.getInt()
            buflen -= 4


            // We always use direct decoding with JPEG images
            jd.decompress(bufptr, len, pb, r, pb.getPF())
            pb.commitBufferRW(r)
        } else { // Quit on unsupported compression type.
            if (comp_ctl > tightMaxSubencoding) throw Exception("TightDecoder: bad subencoding value received") // "Basic" compression type.

            var palSize = 0
            val palette = ByteBuffer.allocate(256 * 4)
            var useGradient = false
            if ((comp_ctl and tightExplicitFilter) != 0) {
                assert(buflen >= 1)
                val filterId = bufptr.get().toInt()

                when (filterId) {
                    tightFilterPalette -> {
                        assert(buflen >= 1)
                        palSize = bufptr.getInt() + 1
                        buflen -= 4

                        if (pf.is888()) {
                            val tightPalette = ByteBuffer.allocate(palSize * 3)

                            assert(buflen >= tightPalette.capacity())
                            bufptr[tightPalette.array(), 0, tightPalette.capacity()]
                            buflen -= tightPalette.capacity()

                            pf.bufferFromRGB(palette.duplicate(), tightPalette, palSize)
                        } else {
                            val len: Int = palSize * pf.bpp / 8

                            assert(buflen >= len)
                            bufptr[palette.array(), 0, len]
                            buflen -= len
                        }
                    }

                    tightFilterGradient -> useGradient = true
                    tightFilterCopy -> {}
                    else -> assert(false)
                }
            }
            val netbuf: ByteBuffer
            // Determine if the data should be decompressed or just copied.
            val rowSize: Int = if (palSize != 0) {
                if (palSize <= 2) (r.width() + 7) / 8
                else r.width()
            } else if (pf.is888()) {
                r.width() * 3
            } else {
                r.width() * pf.bpp / 8
            }
            val dataSize: Int = r.height() * rowSize
            if (dataSize < TIGHT_MIN_TO_COMPRESS) {
                assert(buflen >= dataSize)
            } else {
                var ms: MemInStream?

                assert(buflen >= 4)
                val len = bufptr.getInt()
                buflen -= 4

                assert(buflen >= len)
                val streamId = comp_ctl and 0x03
                ms = MemInStream(bufptr.array(), bufptr.position(), len)
                zis[streamId].setUnderlying(ms, len)


                // Allocate netbuf and read in data
                netbuf = ByteBuffer.allocate(dataSize)

                zis[streamId].readBytes(netbuf, dataSize)

                zis[streamId].flushUnderlying()
                zis[streamId].setUnderlying(null, 0)
                ms = null

                bufptr = netbuf.flip()
                buflen = dataSize
            }
            val outbuf = ByteBuffer.allocate(r.area() * pf.bpp / 8)
            val stride: Int = r.width()
            if (palSize == 0) {
                // Truecolor data.
                if (useGradient) {
                    if (pf.is888()) {
                        FilterGradient24(bufptr, pf, outbuf, stride, r)
                    } else {
                        when (pf.bpp) {
                            8 -> assert(false)
                            16 -> FilterGradient(bufptr, pf, outbuf, stride, r)
                            32 -> FilterGradient(bufptr, pf, outbuf, stride, r)
                        }
                    }
                } else {
                    // Copy
                    val ptr = outbuf.duplicate().mark()
                    val srcPtr = bufptr.duplicate()
                    val w: Int = r.width()
                    var h: Int = r.height()
                    if (pf.is888()) {
                        while (h > 0) {
                            pf.bufferFromRGB(ptr.duplicate(), srcPtr.duplicate(), w)
                            ptr.position(ptr.position() + stride * pf.bpp / 8)
                            srcPtr.position(srcPtr.position() + w * 3)
                            h--
                        }
                    } else {
                        while (h > 0) {
                            ptr.put(srcPtr.array(), srcPtr.position(), w * pf.bpp / 8)
                            ptr.reset().position(ptr.position() + stride * pf.bpp / 8).mark()
                            srcPtr.position(srcPtr.position() + w * pf.bpp / 8)
                            h--
                        }
                    }
                }
            } else {
                // Indexed color
                when (pf.bpp) {
                    8 -> FilterPalette8(palette, palSize,
                            bufptr, outbuf, stride, r)

                    16 -> FilterPalette16(palette.asShortBuffer(), palSize,
                            bufptr, outbuf.asShortBuffer(), stride, r)

                    32 -> FilterPalette32(palette.asIntBuffer(), palSize,
                            bufptr, outbuf.asIntBuffer(), stride, r)
                }
            }
            pb.imageRect(pf, r, outbuf.array())
        }
    }

    private fun FilterGradient24(inbuf: ByteBuffer,
                                 pf: com.tigervnc.rfb.PixelFormat, outbuf: ByteBuffer,
                                 stride: Int, r: Rect) {
        var x: Int
        var c: Int
        val prevRow = ByteArray(TIGHT_MAX_WIDTH * 3)
        val thisRow = ByteArray(TIGHT_MAX_WIDTH * 3)
        val pix = ByteBuffer.allocate(3)
        val est = IntArray(3)


        // Set up shortcut variables
        val rectHeight: Int = r.height()
        val rectWidth: Int = r.width()

        var y = 0
        while (y < rectHeight) {
            x = 0
            while (x < rectWidth) {
                /* First pixel in a row */
                if (x == 0) {
                    c = 0
                    while (c < 3) {
                        pix.put(c, (inbuf[y * rectWidth * 3 + c] + prevRow[c]).toByte())
                        thisRow[c] = pix[c]
                        c++
                    }
                    pf.bufferFromRGB(outbuf.duplicate().position(y * stride) as ByteBuffer, pix, 1)
                    x++
                    continue
                }

                c = 0
                while (c < 3) {
                    est[c] = prevRow[x * 3 + c] + pix[c] - prevRow[(x - 1) * 3 + c]
                    if (est[c] > 0xff) {
                        est[c] = 0xff
                    } else if (est[c] < 0) {
                        est[c] = 0
                    }
                    pix.put(c, (inbuf[(y * rectWidth + x) * 3 + c] + est[c]).toByte())
                    thisRow[x * 3 + c] = pix[c]
                    c++
                }
                pf.bufferFromRGB(outbuf.duplicate().position(y * stride + x) as ByteBuffer, pix, 1)
                x++
            }

            System.arraycopy(thisRow, 0, prevRow, 0, prevRow.size)
            y++
        }
    }

    private fun FilterGradient(inbuf: ByteBuffer,
                               pf: com.tigervnc.rfb.PixelFormat, outbuf: ByteBuffer,
                               stride: Int, r: Rect) {
        var x: Int
        var c: Int
        val prevRow = ByteArray(TIGHT_MAX_WIDTH)
        val thisRow = ByteArray(TIGHT_MAX_WIDTH)
        val pix = ByteBuffer.allocate(3)
        val est = IntArray(3)


        // Set up shortcut variables
        val rectHeight: Int = r.height()
        val rectWidth: Int = r.width()

        var y = 0
        while (y < rectHeight) {
            x = 0
            while (x < rectWidth) {
                /* First pixel in a row */
                if (x == 0) {
                    pf.rgbFromBuffer(pix.duplicate(), inbuf.position(y * rectWidth) as ByteBuffer, 1)
                    c = 0
                    while (c < 3) {
                        pix.put(c, (pix[c] + prevRow[c]).toByte())
                        c++
                    }

                    System.arraycopy(pix.array(), 0, thisRow, 0, pix.capacity())
                    pf.bufferFromRGB(outbuf.duplicate().position(y * stride) as ByteBuffer, pix, 1)
                    x++
                    continue
                }

                c = 0
                while (c < 3) {
                    est[c] = prevRow[x * 3 + c] + pix[c] - prevRow[(x - 1) * 3 + c]
                    if (est[c] > 0xff) {
                        est[c] = 0xff
                    } else if (est[c] < 0) {
                        est[c] = 0
                    }
                    c++
                }

                pf.rgbFromBuffer(pix.duplicate(), inbuf.position(y * rectWidth + x) as ByteBuffer, 1)
                c = 0
                while (c < 3) {
                    pix.put(c, (pix[c] + est[c]).toByte())
                    c++
                }

                System.arraycopy(pix.array(), 0, thisRow, x * 3, pix.capacity())

                pf.bufferFromRGB(outbuf.duplicate().position(y * stride + x) as ByteBuffer, pix, 1)
                x++
            }

            System.arraycopy(thisRow, 0, prevRow, 0, prevRow.size)
            y++
        }
    }

    private fun FilterPalette8(palette: ByteBuffer, palSize: Int,
                               inbuf: ByteBuffer, outbuf: ByteBuffer,
                               stride: Int, r: Rect) {
        // Indexed color
        var x: Int
        var h: Int = r.height()
        val w: Int = r.width()
        var b: Int
        val pad = stride - w
        val ptr = outbuf.duplicate()
        var bits: Byte
        val srcPtr = inbuf.duplicate()
        if (palSize <= 2) {
            // 2-color palette
            while (h > 0) {
                x = 0
                while (x < w / 8) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 0) {
                        ptr.put(palette[bits.toInt() shr b and 1])
                        b--
                    }
                    x++
                }
                if (w % 8 != 0) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 8 - w % 8) {
                        ptr.put(palette[bits.toInt() shr b and 1])
                        b--
                    }
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        } else {
            // 256-color palette
            while (h > 0) {
                val endOfRow = ptr.position() + w
                while (ptr.position() < endOfRow) {
                    ptr.put(palette[srcPtr.get().toInt()])
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        }
    }

    private fun FilterPalette16(palette: ShortBuffer, palSize: Int,
                                inbuf: ByteBuffer, outbuf: ShortBuffer,
                                stride: Int, r: Rect) {
        // Indexed color
        var x: Int
        var h: Int = r.height()
        val w: Int = r.width()
        var b: Int
        val pad = stride - w
        val ptr: ShortBuffer = outbuf.duplicate()
        var bits: Byte
        val srcPtr = inbuf.duplicate()
        if (palSize <= 2) {
            // 2-color palette
            while (h > 0) {
                x = 0
                while (x < w / 8) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 0) {
                        ptr.put(palette.get(bits.toInt() shr b and 1))
                        b--
                    }
                    x++
                }
                if (w % 8 != 0) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 8 - w % 8) {
                        ptr.put(palette.get(bits.toInt() shr b and 1))
                        b--
                    }
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        } else {
            // 256-color palette
            while (h > 0) {
                val endOfRow: Int = ptr.position() + w
                while (ptr.position() < endOfRow) {
                    ptr.put(palette.get(srcPtr.get().toInt()))
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        }
    }

    private fun FilterPalette32(palette: IntBuffer, palSize: Int,
                                inbuf: ByteBuffer, outbuf: IntBuffer,
                                stride: Int, r: Rect) {
        // Indexed color
        var x: Int
        var h: Int = r.height()
        val w: Int = r.width()
        var b: Int
        val pad = stride - w
        val ptr: IntBuffer = outbuf.duplicate()
        var bits: Byte
        val srcPtr = inbuf.duplicate()
        if (palSize <= 2) {
            // 2-color palette
            while (h > 0) {
                x = 0
                while (x < w / 8) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 0) {
                        ptr.put(palette.get(bits.toInt() shr b and 1))
                        b--
                    }
                    x++
                }
                if (w % 8 != 0) {
                    bits = srcPtr.get()
                    b = 7
                    while (b >= 8 - w % 8) {
                        ptr.put(palette.get(bits.toInt() shr b and 1))
                        b--
                    }
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        } else {
            // 256-color palette
            while (h > 0) {
                val endOfRow: Int = ptr.position() + w
                while (ptr.position() < endOfRow) {
                    ptr.put(palette.get(srcPtr.get().toInt() and 0xff))
                }
                ptr.position(ptr.position() + pad)
                h--
            }
        }
    }

    companion object {
        const val TIGHT_MAX_WIDTH: Int = 2048
        const val TIGHT_MIN_TO_COMPRESS: Int = 12

        const val tightFill: Int = 0x08
        const val tightJpeg: Int = 0x09
        const val tightMaxSubencoding: Int = 0x09

        // Filters to improve compression efficiency
        const val tightFilterCopy: Int = 0x00
        const val tightFilterPalette: Int = 0x01
        const val tightFilterGradient: Int = 0x02
    }
}

private operator fun Any.invoke() {

}

// Compression control
const val tightExplicitFilter: Int = 0x04