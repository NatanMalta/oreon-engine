package org.oreon.vk.demo;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.vulkan.VkDevice;
import org.oreon.core.model.Mesh;
import org.oreon.core.scenegraph.NodeComponentKey;
import org.oreon.core.scenegraph.Renderable;
import org.oreon.core.util.BufferUtil;
import org.oreon.core.util.MeshGenerator;
import org.oreon.core.vk.core.buffer.VkBuffer;
import org.oreon.core.vk.core.command.CommandBuffer;
import org.oreon.core.vk.core.command.SubmitInfo;
import org.oreon.core.vk.core.context.VkContext;
import org.oreon.core.vk.core.descriptor.DescriptorSet;
import org.oreon.core.vk.core.descriptor.DescriptorSetLayout;
import org.oreon.core.vk.core.image.VkImage;
import org.oreon.core.vk.core.image.VkImageView;
import org.oreon.core.vk.core.image.VkSampler;
import org.oreon.core.vk.core.pipeline.ShaderPipeline;
import org.oreon.core.vk.core.pipeline.VkVertexInput;
import org.oreon.core.vk.core.platform.VkCamera;
import org.oreon.core.vk.core.scenegraph.VkRenderInfo;
import org.oreon.core.vk.core.synchronization.Fence;
import org.oreon.core.vk.core.synchronization.VkSemaphore;
import org.oreon.core.vk.core.util.VkUtil;
import org.oreon.core.vk.wrapper.buffer.VkBufferHelper;
import org.oreon.core.vk.wrapper.command.DrawCmdBuffer;
import org.oreon.core.vk.wrapper.image.VkImageHelper;
import org.oreon.vk.components.gpgpu.fft.FastFourierTransform;
import org.oreon.vk.engine.OffScreenFbo;
import org.oreon.vk.engine.OffScreenRenderPipeline;

public class VkTestObject extends Renderable{

	private FastFourierTransform fft;
	private Fence fence;
	
	private VkBuffer vertexBufferObject;
	private VkBuffer indexBufferObject;
	private OffScreenFbo fbo;
	private OffScreenRenderPipeline pipeline;
	private int indices;
	
	private VkSemaphore waitSemaphore;
	private VkSemaphore signalSemaphore;
	private SubmitInfo submitInfo;

	public VkTestObject() {
		
		signalSemaphore = new VkSemaphore(VkContext.getLogicalDevice().getHandle());
		
		fft = new FastFourierTransform(VkContext.getLogicalDevice().getHandle(),
				VkContext.getPhysicalDevice().getMemoryProperties(), 512, 1000);
		
	    Mesh mesh = MeshGenerator.NDCQuad2Drot180();
	    
	    indices = mesh.getIndices().length;
		
	    fbo = VkContext.getObject(OffScreenFbo.class);
	    
	    VkDevice device = VkContext.getLogicalDevice().getHandle();
		
		VkImage image = VkImageHelper.createSampledImageFromFile(
				device,
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				"images/vulkan-logo.jpg");
		
		VkImageView imageView = new VkImageView(device,
				VK_FORMAT_R8G8B8A8_UNORM, image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		DescriptorSetLayout layout = new DescriptorSetLayout(device,1);
	    layout.addLayoutBinding(0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
	    						VK_SHADER_STAGE_FRAGMENT_BIT);
	    layout.create();
	    
	    VkSampler sampler = new VkSampler(device, VK_FILTER_LINEAR);
	    
	    DescriptorSet set = new DescriptorSet(device,
	    		VkContext.getDescriptorPoolManager().getDescriptorPool("POOL_1").getHandle(),
	    		layout.getHandlePointer());
	    set.updateDescriptorImageBuffer(fft.getDyImageView().getHandle(),
	    		VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
	    		sampler.getHandle(), 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
		
		List<DescriptorSet> descriptorSets = new ArrayList<DescriptorSet>();
		List<DescriptorSetLayout> descriptorSetLayouts = new ArrayList<DescriptorSetLayout>();
		
		descriptorSets.add(VkCamera.getDescriptor().getSet());
		descriptorSets.add(set);
		descriptorSetLayouts.add(VkCamera.getDescriptor().getLayout());
		descriptorSetLayouts.add(layout);
		
		ShaderPipeline shaderPipeline = new ShaderPipeline(device);
	    shaderPipeline.createVertexShader("shaders/vert.spv");
	    shaderPipeline.createFragmentShader("shaders/frag.spv");
	    shaderPipeline.createShaderPipeline();
	    
	    Mesh quad = MeshGenerator.NDCQuad2Drot180();
	    VkVertexInput vertexInput = new VkVertexInput(quad.getVertexLayout());
	    
	    pipeline = new OffScreenRenderPipeline(VkContext.getLogicalDevice().getHandle(),
	    		fbo, VkUtil.createLongBuffer(descriptorSetLayouts), shaderPipeline, vertexInput);
	    
	    ByteBuffer vertexBuffer = BufferUtil.createByteBuffer(mesh.getVertices(), mesh.getVertexLayout());
		ByteBuffer indexBuffer = BufferUtil.createByteBuffer(mesh.getIndices());
		
		vertexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
				VkContext.getLogicalDevice().getHandle(),
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				vertexBuffer, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

        indexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
        		VkContext.getLogicalDevice().getHandle(),
        		VkContext.getPhysicalDevice().getMemoryProperties(),
        		VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
        		VkContext.getLogicalDevice().getTransferQueue(),
        		indexBuffer, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
	    
	    CommandBuffer commandBuffer = new DrawCmdBuffer(
	    		VkContext.getLogicalDevice().getHandle(),
	    		VkContext.getLogicalDevice().getGraphicsCommandPool().getHandle(), 
	    		pipeline.getHandle(), pipeline.getLayoutHandle(),
	    		fbo.getRenderPass().getHandle(),
	    		fbo.getFrameBuffer().getHandle(),
	    		fbo.getWidth(), fbo.getHeight(),
	    		fbo.getAttachmentCount(),
	    		VkUtil.createLongArray(descriptorSets),
	    		vertexBufferObject.getHandle(),
	    		indexBufferObject.getHandle(),
	    		mesh.getIndices().length); 
	    
	    fence = new Fence(VkContext.getLogicalDevice().getHandle());
	    
	    submitInfo = new SubmitInfo(commandBuffer.getHandlePointer());
	    
	    VkRenderInfo renderInfo = new VkRenderInfo(commandBuffer, submitInfo,
	    		VkContext.getLogicalDevice().getGraphicsQueue());
	    
	    addComponent(NodeComponentKey.MAIN_RENDERINFO, renderInfo);
	}
	
	public void update(){
	}
	
	public void render(){
		
		fft.render();

	    super.render();
	}
}