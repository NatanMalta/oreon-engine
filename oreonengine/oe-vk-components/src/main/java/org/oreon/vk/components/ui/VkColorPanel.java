package org.oreon.vk.components.ui;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

import java.nio.ByteBuffer;

import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.oreon.common.ui.UIColorPanel;
import org.oreon.core.math.Vec4f;
import org.oreon.core.model.Vertex.VertexLayout;
import org.oreon.core.scenegraph.NodeComponentType;
import org.oreon.core.util.BufferUtil;
import org.oreon.core.vk.command.CommandBuffer;
import org.oreon.core.vk.command.SubmitInfo;
import org.oreon.core.vk.context.DeviceManager.DeviceType;
import org.oreon.core.vk.context.VkContext;
import org.oreon.core.vk.device.LogicalDevice;
import org.oreon.core.vk.device.VkDeviceBundle;
import org.oreon.core.vk.framebuffer.VkFrameBufferObject;
import org.oreon.core.vk.memory.VkBuffer;
import org.oreon.core.vk.pipeline.ShaderPipeline;
import org.oreon.core.vk.pipeline.VkPipeline;
import org.oreon.core.vk.pipeline.VkVertexInput;
import org.oreon.core.vk.scenegraph.VkRenderInfo;
import org.oreon.core.vk.wrapper.buffer.VkBufferHelper;
import org.oreon.core.vk.wrapper.command.SecondaryDrawIndexedCmdBuffer;
import org.oreon.core.vk.wrapper.pipeline.GraphicsPipelineAlphaBlend;

public class VkColorPanel extends UIColorPanel{

	private VkPipeline graphicsPipeline;
	private CommandBuffer cmdBuffer;
	private SubmitInfo submitInfo;
	
	public VkColorPanel(Vec4f rgba, int xPos, int yPos, int xScaling, int yScaling,
			VkFrameBufferObject fbo) {
		super(rgba, xPos, yPos, xScaling, yScaling);
		
		// flip y-axxis for vulkan coordinate system
		getOrthographicMatrix().set(1, 1, -getOrthographicMatrix().get(1, 1));
		
		VkDeviceBundle deviceBundle = VkContext.getDeviceManager().getDeviceBundle(DeviceType.MAJOR_GRAPHICS_DEVICE);
		LogicalDevice device = deviceBundle.getLogicalDevice();
		VkPhysicalDeviceMemoryProperties memoryProperties = 
				VkContext.getDeviceManager().getPhysicalDevice(DeviceType.MAJOR_GRAPHICS_DEVICE).getMemoryProperties();
		
		ShaderPipeline shaderPipeline = new ShaderPipeline(device.getHandle());
	    shaderPipeline.createVertexShader("shaders/ui/colorPanel.vert.spv");
	    shaderPipeline.createFragmentShader("shaders/ui/colorPanel.frag.spv");
	    shaderPipeline.createShaderPipeline();

	    int pushConstantRange = Float.BYTES * 20;
		ByteBuffer pushConstants = memAlloc(pushConstantRange);
		pushConstants.put(BufferUtil.createByteBuffer(getOrthographicMatrix()));
		pushConstants.putFloat(rgba.getX());
		pushConstants.putFloat(rgba.getY());
		pushConstants.putFloat(rgba.getZ());
		pushConstants.putFloat(rgba.getW());
		pushConstants.flip();
		
		VkVertexInput vertexInput = new VkVertexInput(VertexLayout.POS2D);
		ByteBuffer vertexBuffer = BufferUtil.createByteBuffer(panel.getVertices(), VertexLayout.POS2D);
		ByteBuffer indexBuffer = BufferUtil.createByteBuffer(panel.getIndices());
		
		VkBuffer vertexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
				device.getHandle(), memoryProperties,
				device.getTransferCommandPool().getHandle(),
				device.getTransferQueue(),
				vertexBuffer, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);

        VkBuffer indexBufferObject = VkBufferHelper.createDeviceLocalBuffer(
        		device.getHandle(), memoryProperties,
        		device.getTransferCommandPool().getHandle(),
        		device.getTransferQueue(),
        		indexBuffer, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        
        graphicsPipeline = new GraphicsPipelineAlphaBlend(device.getHandle(),
				shaderPipeline, vertexInput, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
				null,
				fbo.getWidth(), fbo.getHeight(),
				fbo.getRenderPass().getHandle(),
				fbo.getColorAttachmentCount(),
				1, pushConstantRange,
				VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
        
		cmdBuffer = new SecondaryDrawIndexedCmdBuffer(
				device.getHandle(),
				deviceBundle.getLogicalDevice().getGraphicsCommandPool().getHandle(),
				graphicsPipeline.getHandle(), graphicsPipeline.getLayoutHandle(),
				fbo.getFrameBuffer().getHandle(),
				fbo.getRenderPass().getHandle(),
				0,
				null,
				vertexBufferObject.getHandle(), indexBufferObject.getHandle(),
				panel.getIndices().length,
				pushConstants,
				VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
		
		submitInfo = new SubmitInfo();
		submitInfo.setCommandBuffers(cmdBuffer.getHandlePointer());
		
		VkRenderInfo mainRenderInfo = new VkRenderInfo(cmdBuffer);
		addComponent(NodeComponentType.MAIN_RENDERINFO, mainRenderInfo);
	}

}
