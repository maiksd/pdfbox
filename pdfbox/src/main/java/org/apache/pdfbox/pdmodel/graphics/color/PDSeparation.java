/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.color;

import java.awt.Point;
import java.awt.image.BufferedImage;

import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.function.PDFunction;

/**
 * A Separation color space used to specify either additional colorants or for isolating the
 * control of individual colour components of a device colour space for a subtractive device.
 * When such a space is the current colour space, the current colour shall be a single-component
 * value, called a tint, that controls the given colorant or colour components only.
 *
 * @author Ben Litchfield
 * @author John Hewson
 */
public class PDSeparation extends PDSpecialColorSpace
{
    private static final PDColor INITIAL_COLOR = new PDColor(new float[] { 1 });

    // array indexes
    private static final int COLORANT_NAMES = 1;
    private static final int ALTERNATE_CS = 2;
    private static final int TINT_TRANSFORM = 3;

    // fields
    private PDColorSpace alternateColorSpace = null;
    private PDFunction tintTransform = null;

    /**
     * Creates a new Separation color space.
     */
    public PDSeparation()
    {
        array = new COSArray();
        array.add(COSName.SEPARATION);
        array.add(COSName.getPDFName(""));
    }

    /**
     * Creates a new Separation color space from a PDF color space array.
     * @param separation an array containing all separation information
     */
    public PDSeparation(COSArray separation) throws IOException
    {
        array = separation;
        alternateColorSpace = PDColorSpace.create(array.getObject(ALTERNATE_CS));
        tintTransform = PDFunction.create(array.getObject(TINT_TRANSFORM));
    }

    @Override
    public String getName()
    {
        return COSName.SEPARATION.getName();
    }

    @Override
    public int getNumberOfComponents()
    {
        return 1;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent)
    {
        return new float[] { 0, 1 };
    }

    @Override
    public PDColor getInitialColor()
    {
        return INITIAL_COLOR;
    }

    @Override
    public float[] toRGB(float[] value) throws IOException
    {
        float[] altColor = tintTransform.eval(value);
        return alternateColorSpace.toRGB(altColor);
    }

    //
    // WARNING: this method is performance sensitive, modify with care!
    //
    @Override
    public BufferedImage toRGBImage(WritableRaster raster) throws IOException
    {
        // use the tint transform to convert the sample into
        // the alternate color space (this is usually 1:many)
        WritableRaster altRaster = Raster.createBandedRaster(DataBuffer.TYPE_BYTE,
                raster.getWidth(), raster.getHeight(),
                alternateColorSpace.getNumberOfComponents(),
                new Point(0, 0));

        int numAltComponents = alternateColorSpace.getNumberOfComponents();
        int width = raster.getWidth();
        int height = raster.getHeight();
        float[] samples = new float[1];
        int[] alt = new int[numAltComponents];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                raster.getPixel(x, y, samples);
                samples[0] /= 255; // 0..1

                // TODO special colorants: None, All

                float[] result = tintTransform.eval(samples);
                for (int s = 0; s < numAltComponents; s++)
                {
                    // scale to 0..255
                    alt[s] = (int)(result[s] * 255);
                }

                altRaster.setPixel(x, y, alt);
            }
        }

        // convert the alternate color space to RGB
        return alternateColorSpace.toRGBImage(altRaster);
    }

    /**
     * Returns the colorant name.
     * @return the name of the colorant
     */
    public String getColorantName()
    {
        COSName name = (COSName)array.getObject(COLORANT_NAMES);
        return name.getName();
    }

    /**
     * Sets the colorant name.
     * @param name the name of the colorant
     */
    public void setColorantName(String name)
    {
        array.set(1, COSName.getPDFName(name));
    }

    /**
     * Sets the alternate color space.
     * @param colorSpace The alternate color space.
     */
    public void setAlternateColorSpace(PDColorSpace colorSpace)
    {
        alternateColorSpace = colorSpace;
        COSBase space = null;
        if (colorSpace != null)
        {
            space = colorSpace.getCOSObject();
        }
        array.set(ALTERNATE_CS, space);
    }

    /**
     * Sets the tint transform function.
     * @param tint the tint transform function
     */
    public void setTintTransform(PDFunction tint)
    {
        tintTransform = tint;
        array.set(TINT_TRANSFORM, tint);
    }

    @Override
    public String toString()
    {
        return getName() + "{" +
                "\"" + getColorantName() + "\"" + " " +
                alternateColorSpace.getName() + " " +
                tintTransform + "}";
    }
}
