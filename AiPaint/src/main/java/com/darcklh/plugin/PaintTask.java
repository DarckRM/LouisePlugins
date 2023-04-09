package com.darcklh.plugin;

import com.darcklh.louise.Model.Messages.InMessage;
import com.darcklh.louise.Model.Messages.OutMessage;

/**
 * @author DarckLH
 * @date 2022/10/27 1:13
 * @Description
 */
public class PaintTask {
    OutMessage outMessage;
    InMessage inMessage;
    String prompt;
    String negativePrompt;
    String image;
    Integer width;
    Integer height;
    Integer steps;
    String sampler;
    Integer scale;
    Integer n_samples;
    Integer ucPreset;
    Integer seed;
    Double noise;
    Double strength;
    String original_image;
    Integer img_to_steps;

    @Override
    public String toString() {
        String result = "{\"prompt\":\"" + prompt + "\"" +
                ", \"uc\":\"" + negativePrompt + "\"" +
                ", \"width\":" + width +
                ", \"height\":" + height +
                ", \"steps\":" + steps +
                ", \"sampler\":\"" + sampler + "\"" +
                ", \"scale\":" + scale +
                ", \"n_samples\":" + n_samples +
                ", \"ucPreset\":" + ucPreset +
                ", \"seed\":" + seed;
        if (image != null) {
            result += ", \"image\":\"" + image + "\"" +
                    ", \"noise\":" + noise +
                    ", \"strength\":" + strength +
                    '}';
        } else
            result += "}";
        return result;
    }

    public String getOriginal_image() {
        return original_image;
    }

    public void setOriginal_image(String original_image) {
        this.original_image = original_image;
    }

    public Integer getImg_to_steps() {
        return img_to_steps;
    }

    public void setImg_to_steps(Integer img_to_steps) {
        this.img_to_steps = img_to_steps;
    }

    public Double getNoise() {
        return noise;
    }

    public void setNoise(Double noise) {
        this.noise = noise;
    }

    public Double getStrength() {
        return strength;
    }

    public void setStrength(Double strength) {
        this.strength = strength;
    }

    public OutMessage getOutMessage() {
        return outMessage;
    }

    public void setOutMessage(OutMessage outMessage) {
        this.outMessage = outMessage;
    }

    public InMessage getInMessage() {
        return inMessage;
    }

    public void setInMessage(InMessage inMessage) {
        this.inMessage = inMessage;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getNegativePrompt() {
        return negativePrompt;
    }

    public void setNegativePrompt(String negativePrompt) {
        this.negativePrompt = negativePrompt;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getSteps() {
        return steps;
    }

    public void setSteps(Integer steps) {
        this.steps = steps;
    }

    public String getSampler() {
        return sampler;
    }

    public void setSampler(String sampler) {
        this.sampler = sampler;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public Integer getN_samples() {
        return n_samples;
    }

    public void setN_samples(Integer n_samples) {
        this.n_samples = n_samples;
    }

    public Integer getUcPreset() {
        return ucPreset;
    }

    public void setUcPreset(Integer ucPreset) {
        this.ucPreset = ucPreset;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }
}
