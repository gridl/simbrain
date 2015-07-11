/*
 * Part of Simbrain--a java-based neural network kit
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.network.neuron_update_rules;

import org.simbrain.network.core.Neuron;
import org.simbrain.network.core.SpikingNeuronUpdateRule;
import org.simbrain.network.neuron_update_rules.interfaces.NoisyUpdateRule;
import org.simbrain.util.randomizer.Randomizer;


public class IzhikevichRule extends SpikingNeuronUpdateRule implements
    NoisyUpdateRule {

    /** Recovery. */
    private double recovery;

    /** W. */
    private double w;

    /** V. */
    private double v;

    /** Constant background current. KEEP */
    private double iBg = ...;

    /** Threshold value to signal a spike. KEEP */
    private double threshold = 30;

    /** Noise dialog. */
    private Randomizer noiseGenerator = new Randomizer();

    /** Add noise to the neuron. */
    private boolean addNoise;

    /**
     * {@inheritDoc}
     */
    public IzhikevichRule deepCopy() {
        IzhikevichRule in = new IzhikevichRule();
        in.setA(getA());
        in.setB(getB());
        in.setC(getC());
        in.setD(getD());
        in.setAddNoise(getAddNoise());
        in.noiseGenerator = new Randomizer(noiseGenerator);

        return in;
    }
    private double timeStep;
    private double inputs = 0;
    private double val;
    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final Neuron neuron) {
        timeStep = neuron.getNetwork().getTimeStep();
//        final boolean refractory = getLastSpikeTime() + refractoryPeriod
//                >= neuron.getNetwork().getTime();
        final double activation = neuron.getActivation();
        double inputs = 0;
        inputs = inputType.getInput(neuron);
        if (addNoise) {
            inputs += noiseGenerator.getRandom();
        }
        inputs += iBg;
        recovery += (timeStep * (a * ((b * activation) - recovery)));

        val = activation
            + (timeStep * (((.04 * (activation * activation))
                + (5 * activation) + 140)
                - recovery + inputs));
        // You want this
        if (val >= threshold) {
            val = c;
            recovery += d;
            neuron.setSpkBuffer(true);
            setHasSpiked(true, neuron);
        } else {
            neuron.setSpkBuffer(false);
            setHasSpiked(false, neuron);
        }
        //till here
        neuron.setBuffer(val);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getRandomValue() {
        // Equal chance of spiking or not spiking, taking on any value between
        // the resting potential and the threshold if not.
        return 2 * (threshold - c) * Math.random() + c;
    }

    /**
     * @return Returns the a.
     */
    public double getA() {
        return a;
    }

    /**
     * @param a The a to set.
     */
    public void setA(final double a) {
        this.a = a;
    }

    /**
     * @return Returns the b.
     */
    public double getB() {
        return b;
    }

    /**
     * @param b The b to set.
     */
    public void setB(final double b) {
        this.b = b;
    }

    /**
     * @return Returns the c.
     */
    public double getC() {
        return c;
    }

    /**
     * @param c The c to set.
     */
    public void setC(final double c) {
        this.c = c;
    }

    /**
     * @return Returns the d.
     */
    public double getD() {
        return d;
    }

    /**
     * @param d The d to set.
     */
    public void setD(final double d) {
        this.d = d;
    }

    public double getiBg() {
		return iBg;
	}

	public void setiBg(double iBg) {
		this.iBg = iBg;
	}

	/**
     * @return Returns the addNoise.
     */
    public boolean getAddNoise() {
        return addNoise;
    }

    /**
     * @param addNoise The addNoise to set.
     */
    public void setAddNoise(final boolean addNoise) {
        this.addNoise = addNoise;
    }

    /**
     * @return Returns the noiseGenerator.
     */
    public Randomizer getNoiseGenerator() {
        return noiseGenerator;
    }

    /**
     * @param noiseGenerator The noiseGenerator to set.
     */
    public void setNoiseGenerator(final Randomizer noiseGenerator) {
        this.noiseGenerator = noiseGenerator;
    }

    @Override
    public String getDescription() {
        return "Izhikevich";
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getRefractoryPeriod() {
        return refractoryPeriod;
    }

    public void setRefractoryPeriod(double refractoryPeriod) {
        this.refractoryPeriod = refractoryPeriod;
    }

}