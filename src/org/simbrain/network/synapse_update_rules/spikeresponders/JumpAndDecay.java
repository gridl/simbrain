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
package org.simbrain.network.synapse_update_rules.spikeresponders;

import org.simbrain.network.core.Synapse;
import org.simbrain.util.UserParameter;

/**
 * <b>JumpAndDecay</b>.
 */
public class JumpAndDecay extends SpikeResponder {

    /**
     * Jump height value.
     */
    @UserParameter(label = "Jump Height",
        description = "This value is multiplied by the strength to determine the total instantaneous rise in a"
            + " post-synaptic response to an action potential or spike.",
        defaultValue = "1", order = 1)
    private double jumpHeight;

    /**
     * Base line value.
     */
    @UserParameter(label = "Base-Line",
        description = "The post-synaptic response value when no spike have occurred. Alternatively, the "
            + "post synaptic response to which decays to over time.",
        defaultValue = "0.0001", order = 2)
    private double baseLine;

    /**
     * Rate at which synapse will decay (ms).
     */
    @UserParameter(label = "Time Constant",
        description = "The time constant of decay and recovery (ms).",
        defaultValue = "3", order = 3)
    private double timeConstant;

    /**
     * {@inheritDoc}
     */
    @Override
    public JumpAndDecay deepCopy() {
        JumpAndDecay jad = new JumpAndDecay();
        jad.setBaseLine(this.getBaseLine());
        jad.setJumpHeight(this.getJumpHeight());
        jad.setTimeConstant(this.getTimeConstant());
        return jad;
    }

    @Override
    public void update(final Synapse s) {
        value = s.getPsr();
        if (s.getSource().isSpike()) {
            value = jumpHeight * s.getStrength();
        } else {
            double timeStep = s.getParentNetwork().getTimeStep();
            value += timeStep * (baseLine - value) / timeConstant;
        }
        s.setPsr(value);
    }

    @Override
    public String getDescription() {
        return "Jump and Decay";
    }

    /**
     * @return Returns the baseLine.
     */
    public double getBaseLine() {
        return baseLine;
    }

    /**
     * @param baseLine The baseLine to set.
     */
    public void setBaseLine(final double baseLine) {
        this.baseLine = baseLine;
    }

    /**
     * @return Returns the jumpHeight.
     */
    public double getJumpHeight() {
        return jumpHeight;
    }

    /**
     * @param jumpHeight The jumpHeight to set.
     */
    public void setJumpHeight(final double jumpHeight) {
        this.jumpHeight = jumpHeight;
    }

    /**
     * @return Name of synapse type.
     */
    public String getName() {
        return "Jump and decay";
    }

    /**
     * @return the time constant of the exponential decay of the post synaptic
     * response
     */
    public double getTimeConstant() {
        return timeConstant;
    }

    /**
     * @param decayTimeConstant the new time constant of the exponential decay
     *                          of the post synaptic response
     */
    public void setTimeConstant(double decayTimeConstant) {
        this.timeConstant = decayTimeConstant;
    }

}
