/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.workstation.system;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.terasology.asset.Assets;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.rendering.nui.layouts.FlowLayout;
import org.terasology.rendering.nui.widgets.UIImage;
import org.terasology.workstation.component.ProcessDefinitionComponent;
import org.terasology.workstation.event.WorkstationProcessRequest;
import org.terasology.workstation.process.DescribeProcess;
import org.terasology.workstation.process.ErrorCheckingProcessPart;
import org.terasology.workstation.process.InvalidProcessException;
import org.terasology.workstation.process.InvalidProcessPartException;
import org.terasology.workstation.process.ProcessPart;
import org.terasology.workstation.process.ProcessPartDescription;
import org.terasology.workstation.process.ValidateProcess;
import org.terasology.workstation.process.WorkstationProcess;
import org.terasology.workstation.process.fluid.ValidateFluidInventoryItem;
import org.terasology.workstation.process.inventory.ValidateInventoryItem;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ProcessPartWorkstationProcess implements WorkstationProcess, ValidateInventoryItem, ValidateFluidInventoryItem, DescribeProcess, ValidateProcess {
    private String id;
    private String processType;
    private List<ProcessPart> processParts = new LinkedList<>();

    ProcessPartWorkstationProcess(Prefab prefab) throws InvalidProcessPartException {
        id = "Prefab:" + prefab.getURI().toSimpleString();
        for (Component component : prefab.iterateComponents()) {
            if (component instanceof ProcessPart) {
                if (component instanceof ErrorCheckingProcessPart) {
                    ErrorCheckingProcessPart errorChecking = (ErrorCheckingProcessPart) component;
                    errorChecking.checkForErrors();
                }
                processParts.add((ProcessPart) component);
            } else if (component instanceof ProcessDefinitionComponent) {
                processType = ((ProcessDefinitionComponent) component).processType;
            }
        }
    }

    @Override
    public boolean isResponsibleForSlot(EntityRef workstation, int slotNo) {
        for (ProcessPart processPart : processParts) {
            if (processPart instanceof ValidateInventoryItem) {
                if (((ValidateInventoryItem) processPart).isResponsibleForSlot(workstation, slotNo)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isValid(EntityRef workstation, int slotNo, EntityRef instigator, EntityRef item) {
        for (ProcessPart processPart : processParts) {
            if (processPart instanceof ValidateInventoryItem) {
                final ValidateInventoryItem validateInventory = (ValidateInventoryItem) processPart;
                if (validateInventory.isResponsibleForSlot(workstation, slotNo)) {
                    if (validateInventory.isValid(workstation, slotNo, instigator, item)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean isValid(EntityRef instigator, EntityRef workstation) {
        for (ProcessPart part : processParts) {
            if (!part.validateBeforeStart(instigator, workstation, EntityRef.NULL)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isResponsibleForFluidSlot(EntityRef workstation, int slotNo) {
        for (ProcessPart processPart : processParts) {
            if (processPart instanceof ValidateFluidInventoryItem) {
                if (((ValidateFluidInventoryItem) processPart).isResponsibleForFluidSlot(workstation, slotNo)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isValidFluid(EntityRef workstation, int slotNo, EntityRef instigator, String fluidType) {
        for (ProcessPart processPart : processParts) {
            if (processPart instanceof ValidateFluidInventoryItem) {
                final ValidateFluidInventoryItem validateInventory = (ValidateFluidInventoryItem) processPart;
                if (validateInventory.isResponsibleForFluidSlot(workstation, slotNo)) {
                    if (validateInventory.isValidFluid(workstation, slotNo, instigator, fluidType)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String getProcessType() {
        return processType;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long startProcessingManual(EntityRef instigator, EntityRef workstation, WorkstationProcessRequest
            request, EntityRef processEntity) throws InvalidProcessException {
        return startProcessing(instigator, workstation, processEntity);
    }

    @Override
    public long startProcessingAutomatic(EntityRef workstation, EntityRef processEntity) throws
            InvalidProcessException {
        return startProcessing(workstation, workstation, processEntity);
    }

    private long startProcessing(EntityRef instigator, EntityRef workstation, EntityRef processEntity) throws InvalidProcessException {
        for (ProcessPart processPart : processParts) {
            if (!processPart.validateBeforeStart(instigator, workstation, processEntity)) {
                throw new InvalidProcessException();
            }
        }

        long duration = 0;
        for (ProcessPart processPart : processParts) {
            duration += processPart.getDuration(instigator, workstation, processEntity);
        }

        for (ProcessPart processPart : processParts) {
            processPart.executeStart(instigator, workstation, processEntity);
        }

        return duration;
    }

    @Override
    public void finishProcessing(EntityRef instigator, EntityRef workstation, EntityRef processEntity) {
        for (ProcessPart processPart : processParts) {
            processPart.executeEnd(instigator, workstation, processEntity);
        }
    }

    @Override
    public ProcessPartDescription getOutputDescription() {
        FlowLayout flowLayout = new FlowLayout();
        Set<String> descriptions = Sets.newHashSet();
        for (ProcessPart part : processParts) {
            if (part instanceof DescribeProcess) {
                ProcessPartDescription description = ((DescribeProcess) part).getOutputDescription();
                if (description != null) {
                    descriptions.add(description.toString());
                    flowLayout.addWidget(description.getWidget(), null);
                }
            }
        }
        return new ProcessPartDescription(Joiner.on(", ").join(descriptions), flowLayout);
    }

    @Override
    public ProcessPartDescription getInputDescription() {
        UIImage plus = new UIImage();
        plus.setImage(Assets.getTextureRegion("workstation:plus"));
        FlowLayout flowLayout = new FlowLayout();
        Set<String> descriptions = Sets.newHashSet();
        boolean isFirst = true;
        for (ProcessPart part : processParts) {
            if (part instanceof DescribeProcess) {
                ProcessPartDescription description = ((DescribeProcess) part).getInputDescription();
                if (description != null) {
                    if (!isFirst)
                        flowLayout.addWidget(plus, null);
                    isFirst = false;
                    descriptions.add(description.toString());
                    flowLayout.addWidget(description.getWidget(), null);
                }
            }
        }
        return new ProcessPartDescription(Joiner.on(" + ").join(descriptions), flowLayout);
    }

    @Override
    public int getComplexity() {
        int complexity = 0;
        for (ProcessPart part : processParts) {
            if (part instanceof DescribeProcess) {
                complexity += ((DescribeProcess) part).getComplexity();
            }
        }
        return complexity;
    }
}
